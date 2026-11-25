// Business API: create consent requests, read results,
// read the audit log. Auth: Bearer lc_test_...

import { Router } from 'express';
import mongoose from 'mongoose';
import { wrap, HttpError } from '../middleware/errors.js';
import { businessAuth } from '../middleware/businessAuth.js';
import { Request, Resident, Event } from '../models/index.js';
import { lookupResident, lookupBatch } from '../lib/sandbox.js';
import {
  assertKnownClaims,
  deriveClaims,
} from '../lib/claims.js';
import { hashLagId } from '../lib/crypto.js';
import { logEvent, EVENT_TYPES } from '../lib/events.js';
import {
  expireIfDue,
  businessView,
  DEFAULT_TTL_MIN,
  MAX_TTL_MIN,
} from '../lib/requests.js';

export const businessRouter = Router();
businessRouter.use(businessAuth);

// ---------- POST /v1/requests ----------
// { lagId, claims, requiredClaims?, purpose,
//   expectedName?, reference?, ttlMinutes? }
businessRouter.post('/requests', wrap(async (req, res) => {
  const body = req.body || {};
  const lagId = String(body.lagId || '').trim();
  const purpose = String(body.purpose || '').trim();
  const claims = [...new Set(body.claims || [])];
  const requiredClaims = [...new Set(body.requiredClaims || [])];

  if (!lagId) {
    throw new HttpError(400, 'bad_request', 'lagId is required');
  }
  if (purpose.length < 3 || purpose.length > 140) {
    throw new HttpError(
      400, 'bad_purpose', 'purpose must be 3 to 140 characters'
    );
  }
  if (!Array.isArray(body.claims) || claims.length === 0) {
    throw new HttpError(400, 'no_claims', 'Ask for at least one claim');
  }
  assertKnownClaims(claims);
  const notAsked = requiredClaims.filter((c) => !claims.includes(c));
  if (notAsked.length) {
    throw new HttpError(
      400,
      'bad_required_claims',
      `requiredClaims must also be in claims: ${notAsked.join(', ')}`
    );
  }
  if (claims.includes('name_match') && !body.expectedName) {
    throw new HttpError(
      400,
      'expected_name_needed',
      'name_match needs expectedName'
    );
  }

  // The LagID must exist in the registry
  const record = await lookupResident(lagId);
  if (!record) {
    throw new HttpError(404, 'resident_not_found', 'No such LagID');
  }

  // ...and the resident must have linked a wallet
  const resident = await Resident.findOne({
    lagIdHash: hashLagId(lagId),
  });
  if (!resident) {
    throw new HttpError(
      409,
      'resident_not_linked',
      'This resident has not set up LagID Connect yet'
    );
  }

  const ttl = Math.min(
    Math.max(Number(body.ttlMinutes) || DEFAULT_TTL_MIN, 1),
    MAX_TTL_MIN
  );

  const request = await Request.create({
    businessId: req.business._id,
    residentId: resident._id,
    claims,
    requiredClaims,
    purpose,
    expectedName: body.expectedName || undefined,
    reference: body.reference ? String(body.reference) : undefined,
    expiresAt: new Date(Date.now() + ttl * 60 * 1000),
  });

  await logEvent({
    type: EVENT_TYPES.requestCreated,
    actor: 'business',
    businessId: req.business._id,
    residentId: resident._id,
    requestId: request._id,
    result: 'pending',
    meta: { claims, purpose },
  });

  res.status(201).json({ request: businessView(request) });
}));

// ---------- GET /v1/requests ----------
businessRouter.get('/requests', wrap(async (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 50, 100);
  const filter = { businessId: req.business._id };
  if (req.query.status) filter.status = String(req.query.status);

  const list = await Request.find(filter)
    .sort({ createdAt: -1 })
    .limit(limit);
  const fresh = await Promise.all(
    list.map((r) => expireIfDue(r, req.business))
  );
  res.json({ requests: fresh.map(businessView) });
}));

// ---------- GET /v1/requests/:id ----------
businessRouter.get('/requests/:id', wrap(async (req, res) => {
  if (!mongoose.isValidObjectId(req.params.id)) {
    throw new HttpError(404, 'request_not_found', 'No such request');
  }
  const request = await Request.findOne({
    _id: req.params.id,
    businessId: req.business._id,
  });
  if (!request) {
    throw new HttpError(404, 'request_not_found', 'No such request');
  }
  const fresh = await expireIfDue(request, req.business);
  res.json({ request: businessView(fresh) });
}));

// ---------- GET /v1/events ----------
// The business's audit log. Never shows other businesses.
businessRouter.get('/events', wrap(async (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 50, 200);
  const filter = { businessId: req.business._id };
  if (req.query.type) filter.type = String(req.query.type);

  const events = await Event.find(filter)
    .sort({ at: -1 })
    .limit(limit)
    .lean();

  res.json({
    events: events.map((e) => ({
      id: String(e._id),
      type: e.type,
      actor: e.actor,
      requestId: e.requestId ? String(e.requestId) : null,
      result: e.result || null,
      reason: e.reason || null,
      at: e.at,
    })),
  });
}));

// ---------- GET /v1/me ----------
businessRouter.get('/me', (req, res) => {
  const b = req.business;
  res.json({
    id: String(b._id),
    name: b.name,
    category: b.category,
    verified: b.verified,
    keyPrefix: req.apiKey.prefix,
  });
});

// ---------- GET /v1/stats ----------
businessRouter.get('/stats', wrap(async (req, res) => {
  const businessId = req.business._id;

  const byStatus = await Request.aggregate([
    { $match: { businessId } },
    { $group: { _id: '$status', n: { $sum: 1 } } },
  ]);
  const requests = { pending: 0, approved: 0, declined: 0, expired: 0 };
  for (const row of byStatus) requests[row._id] = row.n;

  const byGate = await Event.aggregate([
    { $match: { businessId, type: EVENT_TYPES.gateResult } },
    { $group: { _id: '$result', n: { $sum: 1 } } },
  ]);
  const gate = { granted: 0, denied: 0 };
  for (const row of byGate) gate[row._id] = row.n;

  const batches = await Event.find({
    businessId,
    type: EVENT_TYPES.batchVerified,
  }).lean();
  const batchChecks = batches.reduce(
    (sum, e) => sum + (e.meta?.count || 0), 0
  );

  res.json({ requests, gate, batchChecks });
}));

// ---------- POST /v1/verify/batch ----------
// { items: [{ lagId, expectedName? }] }  (1 to 100)
// Uses the sandbox's batch lookup. Answers are minimised:
// found / verified / name match / adult. No names, phones
// or other personal data come back.
businessRouter.post('/verify/batch', wrap(async (req, res) => {
  const items = Array.isArray(req.body?.items) ? req.body.items : [];
  if (items.length === 0 || items.length > 100) {
    throw new HttpError(
      400, 'bad_batch', 'Send 1 to 100 items: [{ lagId, expectedName }]'
    );
  }

  const ids = items.map((i) => String(i?.lagId ?? '').trim());
  const { results } = await lookupBatch(ids);

  const byId = new Map(
    results.map((r) => [String(r.lagId).trim().toUpperCase(), r])
  );

  const out = items.map((item, index) => {
    const lagId = ids[index];
    const hit = results[index] &&
      String(results[index].lagId).trim().toUpperCase() === lagId.toUpperCase()
      ? results[index]
      : byId.get(lagId.toUpperCase());
    const status = hit?.status || 'NOT_FOUND';

    if (status !== 'FOUND' || !hit.resident) {
      return {
        lagId,
        status,
        verified: false,
        nameMatch: null,
        over18: null,
      };
    }

    const wanted = ['lagos_resident', 'age_over_18'];
    if (item.expectedName) wanted.push('name_match');
    const claims = deriveClaims(hit.resident, wanted, {
      expectedName: item.expectedName,
    });

    return {
      lagId,
      status,
      verified: claims.lagos_resident,
      nameMatch: item.expectedName ? claims.name_match : null,
      over18: claims.age_over_18,
    };
  });

  const summary = {
    total: out.length,
    verified: out.filter((r) => r.verified).length,
    nameMismatch: out.filter((r) => r.nameMatch === false).length,
    notFound: out.filter((r) => r.status === 'NOT_FOUND').length,
    invalid: out.filter((r) => r.status === 'INVALID_ID').length,
  };

  await logEvent({
    type: EVENT_TYPES.batchVerified,
    actor: 'business',
    businessId: req.business._id,
    result: 'done',
    meta: { count: out.length, ...summary },
  });

  res.json({ summary, results: out });
}));
