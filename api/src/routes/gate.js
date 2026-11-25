// Gate API, used by the Verifier app and the Gate Monitor.
//
//   POST /v1/gate/verify   { code }        (Bearer API key)
//   POST /v1/gate/results  { passId, granted, reason, faceScore }
//   GET  /v1/gate/stream?key=lc_test_...  (Server-Sent Events)

import { Router } from 'express';
import mongoose from 'mongoose';
import { wrap, HttpError } from '../middleware/errors.js';
import { businessAuth } from '../middleware/businessAuth.js';
import { ApiKey, Business, Presentation } from '../models/index.js';
import { sha256 } from '../lib/crypto.js';
import { logEvent, EVENT_TYPES } from '../lib/events.js';
import { addGatePage, broadcastGate } from '../lib/gateStream.js';

export const gateRouter = Router();

// ---------- live stream for the Gate Monitor ----------
// EventSource can't send headers, so the key comes in the
// query string. Fine for the sandbox; use a short-lived
// monitor token in production.
gateRouter.get('/stream', wrap(async (req, res) => {
  const key = String(req.query.key || '');
  const apiKey = key
    ? await ApiKey.findOne({ keyHash: sha256(key), revokedAt: null })
    : null;
  const business = apiKey && await Business.findById(apiKey.businessId);
  if (!business) {
    throw new HttpError(401, 'invalid_api_key', 'API key not valid');
  }

  res.set({
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  res.flushHeaders();
  res.write(`event: hello\ndata: ${JSON.stringify({
    business: business.name,
  })}\n\n`);

  const remove = addGatePage(business._id, res);
  const ping = setInterval(() => res.write(': ping\n\n'), 25_000);
  req.on('close', () => {
    clearInterval(ping);
    remove();
  });
}));

gateRouter.use(businessAuth);

// ---------- POST /v1/gate/verify ----------
// Redeems a pass code once. Returns who it belongs to and
// the face template for the live match.
gateRouter.post('/verify', wrap(async (req, res) => {
  const code = String(req.body?.code || '').trim();
  if (!/^LC1_[A-Za-z0-9_-]{10,40}$/.test(code)) {
    return deny(req, res, null, 'Not a LagID Connect pass');
  }

  const now = new Date();
  const pass = await Presentation.findOneAndUpdate(
    { code, usedAt: null, expiresAt: { $gt: now } },
    { usedAt: now, usedBy: req.business._id },
    { new: true }
  );

  if (!pass) {
    const seen = await Presentation.findOne({ code });
    const reason = !seen
      ? 'Pass not recognised'
      : seen.usedAt
        ? 'Pass already used'
        : 'Pass expired';
    return deny(req, res, seen, reason);
  }

  if (pass.claims?.lagos_resident !== true) {
    return deny(req, res, pass, 'Not a verified Lagos resident');
  }

  res.json({
    valid: true,
    passId: String(pass._id),
    name: pass.displayName,
    lagIdLast4: pass.lagIdLast4,
    claims: pass.claims,
    faceTemplate: pass.faceTemplate,
  });
}));

async function deny(req, res, pass, reason) {
  await logEvent({
    type: EVENT_TYPES.gateResult,
    actor: 'verifier',
    businessId: req.business._id,
    residentId: pass?.residentId,
    result: 'denied',
    reason,
  });
  broadcastGate(req.business._id, {
    granted: false,
    name: pass?.displayName || null,
    lagIdLast4: pass?.lagIdLast4 || null,
    reason,
    at: new Date().toISOString(),
  });
  res.json({ valid: false, reason });
}

// ---------- POST /v1/gate/results ----------
// The verifier reports the face-match outcome.
gateRouter.post('/results', wrap(async (req, res) => {
  const { passId, granted, reason, faceScore } = req.body || {};
  if (!mongoose.isValidObjectId(passId)) {
    throw new HttpError(400, 'bad_pass_id', 'passId is required');
  }

  const pass = await Presentation.findOne({
    _id: passId,
    usedBy: req.business._id,
  });
  if (!pass) {
    throw new HttpError(404, 'pass_not_found', 'No such pass');
  }

  // The template has done its job: drop it now
  await Presentation.updateOne(
    { _id: pass._id },
    { $unset: { faceTemplate: 1 } }
  );

  const ok = granted === true;
  const why = ok ? 'Face matched' : String(reason || 'Face did not match');

  await logEvent({
    type: EVENT_TYPES.gateResult,
    actor: 'verifier',
    businessId: req.business._id,
    residentId: pass.residentId,
    result: ok ? 'granted' : 'denied',
    reason: why,
    meta: {
      faceScore: typeof faceScore === 'number' ? faceScore : null,
    },
  });

  const shown = broadcastGate(req.business._id, {
    granted: ok,
    name: pass.displayName,
    lagIdLast4: pass.lagIdLast4,
    reason: why,
    faceScore: typeof faceScore === 'number' ? faceScore : null,
    at: new Date().toISOString(),
  });

  res.json({ ok: true, monitors: shown });
}));
