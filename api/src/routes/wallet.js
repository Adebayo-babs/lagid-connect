// Wallet API, used by the Resident Wallet app.
// /link/* is open (no device yet); everything else needs
// a device-signed token (see middleware/walletAuth.js).

import { Router } from 'express';
import mongoose from 'mongoose';
import { config, isDev } from '../config.js';
import { wrap, HttpError } from '../middleware/errors.js';
import { walletAuth } from '../middleware/walletAuth.js';
import { businessAudience } from '../middleware/businessAuth.js';
import {
  Resident,
  Device,
  Business,
  Request,
  Grant,
  Credential,
  Event,
  Presentation,
} from '../models/index.js';
import { lookupResident } from '../lib/sandbox.js';
import { deriveClaims } from '../lib/claims.js';
import {
  hashLagId,
  pairwiseSub,
  lastFour,
  maskPhone,
  sha256,
  randomId,
} from '../lib/crypto.js';
import { signToken } from '../lib/keys.js';
import { startOtp, checkOtp } from '../lib/otp.js';
import {
  cleanDevicePublicKey,
  verifyDeviceSignature,
} from '../lib/deviceKeys.js';
import { logEvent, EVENT_TYPES } from '../lib/events.js';
import { sendWebhook } from '../lib/webhooks.js';
import { expireIfDue, walletView } from '../lib/requests.js';

export const walletRouter = Router();

const digits = (s) => String(s || '').replace(/\D/g, '');
const samePhone = (a, b) =>
  digits(a).slice(-10) === digits(b).slice(-10);

// ======================================================
// Linking
// ======================================================

// POST /v1/wallet/link/start  { lagId, phone }
walletRouter.post('/link/start', wrap(async (req, res) => {
  const lagId = String(req.body?.lagId || '').trim();
  const phone = String(req.body?.phone || '').trim();
  if (!lagId || !phone) {
    throw new HttpError(400, 'bad_request', 'lagId and phone are required');
  }

  const record = await lookupResident(lagId);
  if (!record) {
    throw new HttpError(404, 'resident_not_found', 'No such LagID');
  }
  if (String(record.status).toLowerCase() !== 'active') {
    throw new HttpError(403, 'record_inactive', 'This LagID is not active');
  }
  if (record.verified === false) {
    throw new HttpError(
      403,
      'record_unverified',
      'This LagID record is not verified yet'
    );
  }
  // If the record has a phone, it must match
  if (record.phone && !samePhone(record.phone, phone)) {
    throw new HttpError(
      403,
      'phone_mismatch',
      'Phone does not match the LagID record'
    );
  }

  const { linkId, code, expiresIn } = startOtp({ lagId });
  const showCode = config.sandbox.mock || isDev || config.demoOtp;
  if (showCode) console.log(`[otp] ${lagId}: ${code}`);

  res.json({
    linkId,
    expiresIn,
    firstName: record.firstName || null,
    phoneMasked: maskPhone(record.phone || phone),
    ...(showCode ? { devCode: code } : {}),
  });
}));

// POST /v1/wallet/link/complete
// { linkId, code, publicKey (JWK), faceMatch: { passed, score } }
walletRouter.post('/link/complete', wrap(async (req, res) => {
  const { linkId, code, publicKey, faceMatch } = req.body || {};

  const otp = checkOtp(linkId, code);
  if (otp.error) {
    const status = otp.error === 'wrong_code' ? 401 : 400;
    throw new HttpError(status, otp.error, 'Code check failed');
  }

  if (!faceMatch || faceMatch.passed !== true) {
    throw new HttpError(
      403,
      'face_match_failed',
      'Face match against the LagID photo is required'
    );
  }

  const jwk = cleanDevicePublicKey(publicKey);
  const lagId = otp.data.lagId;

  // Hash of the face template enrolled on the phone.
  // The template itself never leaves the phone at rest.
  const faceHash = /^[a-f0-9]{64}$/.test(faceMatch.templateHash || '')
    ? faceMatch.templateHash
    : undefined;
  const record = await lookupResident(lagId);
  if (!record) {
    throw new HttpError(404, 'resident_not_found', 'No such LagID');
  }

  const lagIdHash = hashLagId(lagId);
  const resident = await Resident.findOneAndUpdate(
    { lagIdHash },
    {
      lagIdHash,
      lagIdLast4: lastFour(lagId),
      displayName: record.fullName,
      phoneMasked: maskPhone(record.phone),
      linkedAt: new Date(),
      ...(faceHash ? { faceHash } : {}),
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );

  // One active phone per resident: retire the old one
  // and everything it held.
  const oldDevices = await Device.find({
    residentId: resident._id,
    status: 'active',
  });
  if (oldDevices.length) {
    const ids = oldDevices.map((d) => d._id);
    await Device.updateMany({ _id: { $in: ids } }, { status: 'retired' });
    await Credential.updateMany(
      { deviceId: { $in: ids }, status: 'active' },
      { status: 'revoked' }
    );
  }

  const device = await Device.create({
    residentId: resident._id,
    publicKey: jwk,
    platform: req.body?.platform || 'android',
  });

  await logEvent({
    type: EVENT_TYPES.walletLinked,
    actor: 'resident',
    residentId: resident._id,
    result: 'linked',
    meta: {
      deviceId: String(device._id),
      faceScore: typeof faceMatch.score === 'number'
        ? faceMatch.score
        : null,
      replacedDevices: oldDevices.length,
    },
  });

  res.status(201).json({
    residentId: String(resident._id),
    deviceId: String(device._id),
    displayName: resident.displayName,
    lagIdLast4: resident.lagIdLast4,
  });
}));

// ======================================================
// Everything below needs a device token
// ======================================================

walletRouter.use(walletAuth);

// GET /v1/wallet/me
walletRouter.get('/me', (req, res) => {
  const r = req.resident;
  res.json({
    residentId: String(r._id),
    displayName: r.displayName,
    lagIdLast4: r.lagIdLast4,
    phoneMasked: r.phoneMasked,
    linkedAt: r.linkedAt,
    deviceId: String(req.device._id),
  });
});

// GET /v1/wallet/requests?status=pending
walletRouter.get('/requests', wrap(async (req, res) => {
  const filter = { residentId: req.resident._id };
  if (req.query.status) filter.status = String(req.query.status);

  const list = await Request.find(filter)
    .sort({ createdAt: -1 })
    .limit(50);

  const bizIds = [...new Set(list.map((r) => String(r.businessId)))];
  const businesses = await Business.find({ _id: { $in: bizIds } });
  const byId = new Map(businesses.map((b) => [String(b._id), b]));

  const out = [];
  for (const r of list) {
    const biz = byId.get(String(r.businessId));
    const fresh = await expireIfDue(r, biz);
    if (req.query.status && fresh.status !== req.query.status) continue;
    out.push(walletView(fresh, biz));
  }
  res.json({ requests: out });
}));

// POST /v1/wallet/requests/:id/decision
// {
//   decision: 'approve' | 'decline',
//   claims: [...],          // chosen claims (approve)
//   lagId: 'LAG...',        // from the phone (approve)
//   signature: '<JWS>'      // device-signed
// }
// The JWS payload must be:
//   { rid, decision, claims, iat }
walletRouter.post('/requests/:id/decision', wrap(async (req, res) => {
  const { decision, signature } = req.body || {};
  const chosen = [...new Set(req.body?.claims || [])].sort();

  if (!['approve', 'decline'].includes(decision)) {
    throw new HttpError(400, 'bad_decision', 'decision must be approve or decline');
  }
  if (!mongoose.isValidObjectId(req.params.id)) {
    throw new HttpError(404, 'request_not_found', 'No such request');
  }

  let request = await Request.findOne({
    _id: req.params.id,
    residentId: req.resident._id,
  });
  if (!request) {
    throw new HttpError(404, 'request_not_found', 'No such request');
  }
  const business = await Business.findById(request.businessId);
  request = await expireIfDue(request, business);
  if (request.status !== 'pending') {
    throw new HttpError(
      409,
      'request_closed',
      `Request is already ${request.status}`
    );
  }

  // The phone must have signed exactly this decision
  const signed = await verifyDeviceSignature(req.device, signature);
  const signedClaims = [...new Set(signed.claims || [])].sort();
  if (
    signed.rid !== String(request._id) ||
    signed.decision !== decision ||
    (decision === 'approve' &&
      signedClaims.join(',') !== chosen.join(','))
  ) {
    throw new HttpError(
      401,
      'signature_mismatch',
      'Signature does not match this decision'
    );
  }

  // ---------- decline ----------
  if (decision === 'decline') {
    const updated = await Request.findOneAndUpdate(
      { _id: request._id, status: 'pending' },
      {
        status: 'declined',
        decidedAt: new Date(),
        residentSignature: signature,
      },
      { new: true }
    );
    if (!updated) {
      throw new HttpError(409, 'request_closed', 'Request already decided');
    }
    await logEvent({
      type: EVENT_TYPES.requestDeclined,
      actor: 'resident',
      businessId: request.businessId,
      residentId: req.resident._id,
      requestId: request._id,
      result: 'declined',
    });
    sendWebhook(business, EVENT_TYPES.requestDeclined, {
      requestId: String(request._id),
      reference: request.reference || null,
    });
    return res.json({ request: walletView(updated, business) });
  }

  // ---------- approve ----------
  const missing = request.requiredClaims.filter((c) => !chosen.includes(c));
  if (missing.length) {
    throw new HttpError(
      400,
      'required_claims_missing',
      `These claims are required: ${missing.join(', ')}`
    );
  }
  const extra = chosen.filter((c) => !request.claims.includes(c));
  if (extra.length || chosen.length === 0) {
    throw new HttpError(
      400,
      'bad_claims',
      'Choose only from the claims that were asked for'
    );
  }

  // We store only a hash of the LagID, so the phone sends
  // it back for a fresh lookup. It must be this resident's.
  const lagId = String(req.body?.lagId || '').trim();
  if (!lagId || hashLagId(lagId) !== req.resident.lagIdHash) {
    throw new HttpError(403, 'lag_id_mismatch', 'LagID does not match');
  }

  const record = await lookupResident(lagId);
  const claims = deriveClaims(record, chosen, {
    expectedName: request.expectedName,
    phoneVerified: true, // checked by one-time code at link
  });

  const token = await signToken(
    {
      purpose: request.purpose,
      reference: request.reference || undefined,
      claims,
    },
    {
      audience: businessAudience(business),
      subject: pairwiseSub(req.resident._id, business._id),
      ttl: '1h',
      jti: String(request._id),
    }
  );

  const updated = await Request.findOneAndUpdate(
    { _id: request._id, status: 'pending' },
    {
      status: 'approved',
      approvedClaims: chosen,
      claimsToken: token,
      decidedAt: new Date(),
      residentSignature: signature,
    },
    { new: true }
  );
  if (!updated) {
    throw new HttpError(409, 'request_closed', 'Request already decided');
  }

  // Standing permission for this business
  await Grant.findOneAndUpdate(
    {
      businessId: business._id,
      residentId: req.resident._id,
      status: 'active',
    },
    { $addToSet: { claims: { $each: chosen } } },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );

  await logEvent({
    type: EVENT_TYPES.requestApproved,
    actor: 'resident',
    businessId: business._id,
    residentId: req.resident._id,
    requestId: request._id,
    result: 'approved',
    meta: { claims: chosen },
  });
  sendWebhook(business, EVENT_TYPES.requestApproved, {
    requestId: String(request._id),
    reference: request.reference || null,
    token,
  });

  res.json({ request: walletView(updated, business) });
}));

// POST /v1/wallet/pass   { lagId, template }
// Issues a single-use gate pass: a short code the phone
// shows as a wave-locked QR. Valid for 30 seconds.
// The face template rides along only for those 30
// seconds, so the gate can match the live face.
const PASS_SECONDS = 30;

walletRouter.post('/pass', wrap(async (req, res) => {
  const lagId = String(req.body?.lagId || '').trim();
  const template = String(req.body?.template || '');

  if (!lagId || hashLagId(lagId) !== req.resident.lagIdHash) {
    throw new HttpError(403, 'lag_id_mismatch', 'LagID does not match');
  }
  if (!req.resident.faceHash) {
    throw new HttpError(
      409,
      'face_not_enrolled',
      'Link again to enrol your face'
    );
  }
  if (sha256(template) !== req.resident.faceHash) {
    throw new HttpError(
      403,
      'face_template_mismatch',
      'Face template does not match the enrolled one'
    );
  }

  // Fresh registry check every time a pass is issued
  const record = await lookupResident(lagId);
  const claims = deriveClaims(
    record,
    ['lagos_resident', 'age_over_18'],
    {}
  );

  const code = randomId('LC1', 15); // ~24 chars, fits the wave QR
  const expiresAt = new Date(Date.now() + PASS_SECONDS * 1000);

  await Presentation.create({
    code,
    residentId: req.resident._id,
    deviceId: req.device._id,
    displayName: req.resident.displayName,
    lagIdLast4: req.resident.lagIdLast4,
    claims,
    faceTemplate: template,
    expiresAt,
  });

  res.status(201).json({ code, expiresIn: PASS_SECONDS, expiresAt });
}));

// GET /v1/wallet/activity
walletRouter.get('/activity', wrap(async (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 50, 200);
  const events = await Event.find({ residentId: req.resident._id })
    .sort({ at: -1 })
    .limit(limit)
    .lean();

  const bizIds = [
    ...new Set(events.filter((e) => e.businessId).map((e) => String(e.businessId))),
  ];
  const businesses = await Business.find({ _id: { $in: bizIds } }).lean();
  const names = new Map(businesses.map((b) => [String(b._id), b.name]));

  res.json({
    activity: events.map((e) => ({
      id: String(e._id),
      type: e.type,
      business: e.businessId ? names.get(String(e.businessId)) || null : null,
      requestId: e.requestId ? String(e.requestId) : null,
      result: e.result || null,
      claims: e.meta?.claims || null,
      purpose: e.meta?.purpose || null,
      at: e.at,
    })),
  });
}));

// GET /v1/wallet/grants
walletRouter.get('/grants', wrap(async (req, res) => {
  const grants = await Grant.find({
    residentId: req.resident._id,
    status: 'active',
  }).lean();
  const businesses = await Business.find({
    _id: { $in: grants.map((g) => g.businessId) },
  }).lean();
  const byId = new Map(businesses.map((b) => [String(b._id), b]));

  res.json({
    grants: grants.map((g) => ({
      id: String(g._id),
      business: byId.get(String(g.businessId))?.name || null,
      claims: g.claims,
      since: g.createdAt,
    })),
  });
}));

// DELETE /v1/wallet/grants/:id
walletRouter.delete('/grants/:id', wrap(async (req, res) => {
  if (!mongoose.isValidObjectId(req.params.id)) {
    throw new HttpError(404, 'grant_not_found', 'No such grant');
  }
  const grant = await Grant.findOneAndUpdate(
    {
      _id: req.params.id,
      residentId: req.resident._id,
      status: 'active',
    },
    { status: 'revoked', revokedAt: new Date() },
    { new: true }
  );
  if (!grant) {
    throw new HttpError(404, 'grant_not_found', 'No such grant');
  }

  const business = await Business.findById(grant.businessId);
  await logEvent({
    type: EVENT_TYPES.grantRevoked,
    actor: 'resident',
    businessId: grant.businessId,
    residentId: req.resident._id,
    result: 'revoked',
  });
  sendWebhook(business, EVENT_TYPES.grantRevoked, {
    subject: pairwiseSub(req.resident._id, grant.businessId),
  });

  res.json({ ok: true });
}));
