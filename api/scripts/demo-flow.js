// End-to-end check of the consent flow, from the terminal.
// It plays both sides: a resident's phone and a business.
//
// 1. Start the API:      npm run dev
// 2. In another window:  npm run demo
//
// Optional: DEMO_API_KEY=lc_test_... npm run demo
// (otherwise a fresh key for Ikeja Heights Estate is made)

import {
  generateKeyPair,
  exportJWK,
  SignJWT,
  CompactSign,
  createRemoteJWKSet,
  jwtVerify,
  decodeJwt,
} from 'jose';
import crypto from 'node:crypto';
import mongoose from 'mongoose';
import { connectDb } from '../src/db.js';
import { Business, ApiKey } from '../src/models/index.js';
import { newApiKey } from '../src/lib/crypto.js';
import { listSampleResidents } from '../src/lib/sandbox.js';

const API = process.env.API_URL || 'http://localhost:4000';
// Picked from the sandbox's sample list at start-up
let RESIDENT; // { lagId, phone, name }
let UNLINKED; // a second LagID that has no wallet

let passed = 0;
let failed = 0;

function check(label, ok, extra = '') {
  if (ok) passed += 1;
  else failed += 1;
  console.log(`${ok ? '  ✔' : '  ✘'} ${label}${extra ? `  ${extra}` : ''}`);
}

async function call(method, path, { body, auth } = {}) {
  const res = await fetch(API + path, {
    method,
    headers: {
      Connection: 'close',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(auth ? { Authorization: `Bearer ${auth}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, json };
}

// ---------- the "phone" ----------

const { publicKey, privateKey } = await generateKeyPair('ES256', {
  extractable: true,
});
let deviceId;

// Stand-in face template: 192 floats, like MobileFaceNet
const faceTemplate = Buffer.from(
  new Float32Array(192).map(() => Math.random() - 0.5).buffer
).toString('base64');
const templateHash = crypto
  .createHash('sha256')
  .update(faceTemplate)
  .digest('hex');

async function deviceToken() {
  return new SignJWT({})
    .setProtectedHeader({ alg: 'ES256', kid: deviceId })
    .setAudience('lagid-connect-wallet')
    .setIssuedAt()
    .setExpirationTime('2m')
    .sign(privateKey);
}

async function signDecision(rid, decision, claims = []) {
  const payload = {
    rid,
    decision,
    claims: [...claims].sort(),
    iat: Math.floor(Date.now() / 1000),
  };
  return new CompactSign(
    new TextEncoder().encode(JSON.stringify(payload))
  )
    .setProtectedHeader({ alg: 'ES256' })
    .sign(privateKey);
}

// ---------- a business API key ----------

async function getApiKey() {
  if (process.env.DEMO_API_KEY) return process.env.DEMO_API_KEY;
  await connectDb();
  const biz = await Business.findOne({ name: 'Ikeja Heights Estate' });
  if (!biz) throw new Error('Run `npm run seed` first');
  const { key, prefix, keyHash } = newApiKey('test');
  await ApiKey.create({ businessId: biz._id, keyHash, prefix });
  await mongoose.disconnect();
  return key;
}

// ======================================================

async function main() {
  const health = await call('GET', '/health');
  if (health.json.db !== 'connected') {
    console.log('API not running or DB not connected. Start `npm run dev`.');
    process.exit(1);
  }

  const apiKey = await getApiKey();
  console.log(`\nAPI ${API}  (sandbox: ${health.json.sandbox})\n`);

  // Use real sample residents: verified, with a phone
  // Dev routes are off on a deployed API: read the sandbox
  // directly with the key in api/.env instead.
  const list = await call('GET', '/v1/dev/residents');
  const residents = list.status === 200
    ? list.json.residents
    : await listSampleResidents();
  const usable = (residents || []).filter(
    (r) => r.verified !== false && r.phone
  );
  if (usable.length < 2) {
    console.log('Need at least two verified sample residents.');
    process.exit(1);
  }
  const pickIndex = Number(process.env.DEMO_INDEX || 0);
  const r0 = usable[pickIndex % usable.length];
  const r1 = usable[(pickIndex + 1) % usable.length];
  RESIDENT = {
    lagId: r0.lagId,
    phone: r0.phone,
    name: `${r0.firstName} ${r0.lastName}`,
  };
  UNLINKED = r1.lagId;
  console.log(`Resident: ${RESIDENT.name} (${RESIDENT.lagId})\n`);

  // ---- 1. Link the wallet ----
  console.log('1. Link the resident’s wallet');
  const start = await call('POST', '/v1/wallet/link/start', {
    body: { lagId: RESIDENT.lagId, phone: RESIDENT.phone },
  });
  check('link started, code sent', start.status === 200, start.json.phoneMasked);

  const wrongPhone = await call('POST', '/v1/wallet/link/start', {
    body: { lagId: RESIDENT.lagId, phone: '08000000000' },
  });
  check('wrong phone is refused', wrongPhone.status === 403);

  const badCode = await call('POST', '/v1/wallet/link/complete', {
    body: {
      linkId: start.json.linkId,
      code: '000000' === start.json.devCode ? '111111' : '000000',
      publicKey: await exportJWK(publicKey),
      faceMatch: { passed: true, score: 0.82 },
    },
  });
  check('wrong code is refused', badCode.status === 401);

  const done = await call('POST', '/v1/wallet/link/complete', {
    body: {
      linkId: start.json.linkId,
      code: start.json.devCode,
      publicKey: await exportJWK(publicKey),
      faceMatch: { passed: true, score: 0.82, templateHash },
    },
  });
  deviceId = done.json.deviceId;
  check('wallet linked', done.status === 201, done.json.displayName);

  const me = await call('GET', '/v1/wallet/me', { auth: await deviceToken() });
  check('device token accepted', me.status === 200);

  const noToken = await call('GET', '/v1/wallet/me');
  check('no device token is refused', noToken.status === 401);

  // ---- 2. Business asks ----
  console.log('\n2. Ikeja Heights asks for proof');
  const noKey = await call('POST', '/v1/requests', { body: {} });
  check('no API key is refused', noKey.status === 401);

  const unlinked = await call('POST', '/v1/requests', {
    auth: apiKey,
    body: {
      lagId: UNLINKED,
      claims: ['lagos_resident'],
      purpose: 'Visitor check',
    },
  });
  check('resident without a wallet gets 409', unlinked.status === 409);

  const created = await call('POST', '/v1/requests', {
    auth: apiKey,
    body: {
      lagId: RESIDENT.lagId,
      claims: ['lagos_resident', 'name_match', 'age_over_18', 'lga'],
      requiredClaims: ['lagos_resident', 'name_match'],
      purpose: 'Tenancy application, Flat 4B',
      expectedName: RESIDENT.name,
      reference: 'APP-4B-001',
    },
  });
  const rid = created.json.request?.id;
  check('request created', created.status === 201, rid);

  // ---- 3. Phone approves ----
  console.log('\n3. The resident approves on the phone (shares 3 of 4)');
  const pending = await call('GET', '/v1/wallet/requests?status=pending', {
    auth: await deviceToken(),
  });
  const seen = pending.json.requests?.find((r) => r.id === rid);
  check('request shows in the wallet', !!seen, seen?.business?.name);

  const chosen = ['lagos_resident', 'name_match', 'age_over_18'];

  const tampered = await call('POST', `/v1/wallet/requests/${rid}/decision`, {
    auth: await deviceToken(),
    body: {
      decision: 'approve',
      claims: [...chosen, 'lga'], // differs from what was signed
      lagId: RESIDENT.lagId,
      signature: await signDecision(rid, 'approve', chosen),
    },
  });
  check('changed claims after signing is refused', tampered.status === 401);

  const missingReq = await call('POST', `/v1/wallet/requests/${rid}/decision`, {
    auth: await deviceToken(),
    body: {
      decision: 'approve',
      claims: ['age_over_18'],
      lagId: RESIDENT.lagId,
      signature: await signDecision(rid, 'approve', ['age_over_18']),
    },
  });
  check('leaving out a required claim is refused', missingReq.status === 400);

  const approve = await call('POST', `/v1/wallet/requests/${rid}/decision`, {
    auth: await deviceToken(),
    body: {
      decision: 'approve',
      claims: chosen,
      lagId: RESIDENT.lagId,
      signature: await signDecision(rid, 'approve', chosen),
    },
  });
  check('approved', approve.status === 200);

  const again = await call('POST', `/v1/wallet/requests/${rid}/decision`, {
    auth: await deviceToken(),
    body: {
      decision: 'decline',
      lagId: RESIDENT.lagId,
      signature: await signDecision(rid, 'decline'),
    },
  });
  check('deciding twice is refused', again.status === 409);

  // ---- 4. Business gets a signed answer ----
  console.log('\n4. Ikeja Heights gets a signed answer');
  const result = await call('GET', `/v1/requests/${rid}`, { auth: apiKey });
  const token = result.json.request?.token;
  check('status approved, token present', result.json.request?.status === 'approved' && !!token);

  const jwks = createRemoteJWKSet(new URL('/.well-known/jwks.json', API));
  const { aud } = decodeJwt(token);
  const { payload } = await jwtVerify(token, jwks, { audience: aud });
  check('token signature verifies with public keys', true);
  check('claims are yes/no answers', payload.claims?.lagos_resident === true &&
    payload.claims?.name_match === true &&
    payload.claims?.age_over_18 === true);
  check('lga was not shared', !('lga' in payload.claims));
  check('no LagID inside the token', !JSON.stringify(payload).includes(RESIDENT.lagId));
  console.log('\n  token payload:', JSON.stringify(payload, null, 2)
    .split('\n').join('\n  '));

  // ---- 5. Decline a second request ----
  console.log('\n5. A second request, declined');
  const second = await call('POST', '/v1/requests', {
    auth: apiKey,
    body: {
      lagId: RESIDENT.lagId,
      claims: ['full_name'],
      purpose: 'Newsletter sign-up',
    },
  });
  const rid2 = second.json.request?.id;
  const decline = await call('POST', `/v1/wallet/requests/${rid2}/decision`, {
    auth: await deviceToken(),
    body: { decision: 'decline', signature: await signDecision(rid2, 'decline') },
  });
  check('declined', decline.status === 200);
  const r2 = await call('GET', `/v1/requests/${rid2}`, { auth: apiKey });
  check('business sees declined, no token', r2.json.request?.status === 'declined' && !r2.json.request?.token);

  // ---- 6. Activity and revoke ----
  console.log('\n6. The resident checks activity and revokes');
  const activity = await call('GET', '/v1/wallet/activity', { auth: await deviceToken() });
  check('activity log has entries', activity.json.activity?.length >= 4,
    `${activity.json.activity?.length} events`);

  const grants = await call('GET', '/v1/wallet/grants', { auth: await deviceToken() });
  const grant = grants.json.grants?.find((g) => g.business === 'Ikeja Heights Estate');
  check('Ikeja Heights has a grant', !!grant, grant?.claims?.join(', '));

  const revoke = await call('DELETE', `/v1/wallet/grants/${grant?.id}`, {
    auth: await deviceToken(),
  });
  check('grant revoked', revoke.status === 200);

  const audit = await call('GET', '/v1/events?limit=10', { auth: apiKey });
  check('business audit log shows the revoke',
    audit.json.events?.some((e) => e.type === 'grant.revoked'));

  // ---- 7. Gate pass ----
  console.log('\n7. Gate pass at Ikeja Heights');
  const badPass = await call('POST', '/v1/wallet/pass', {
    auth: await deviceToken(),
    body: { lagId: RESIDENT.lagId, template: 'not-the-enrolled-face' },
  });
  check('pass refused for a different face template', badPass.status === 403);

  const pass = await call('POST', '/v1/wallet/pass', {
    auth: await deviceToken(),
    body: { lagId: RESIDENT.lagId, template: faceTemplate },
  });
  const code = pass.json.code;
  check('pass issued', pass.status === 201 && !!code, code);
  check('code fits the wave QR (84 chars)', (code || '').length <= 84,
    `${(code || '').length} chars`);

  const gate = await call('POST', '/v1/gate/verify', {
    auth: apiKey, body: { code },
  });
  check('gate accepts the pass', gate.json.valid === true, gate.json.name);
  check('gate gets the face template for matching',
    gate.json.faceTemplate === faceTemplate);

  const replay = await call('POST', '/v1/gate/verify', {
    auth: apiKey, body: { code },
  });
  check('same pass again is refused', replay.json.valid === false,
    replay.json.reason);

  const fake = await call('POST', '/v1/gate/verify', {
    auth: apiKey, body: { code: 'LC1_doesnotexist12345' },
  });
  check('unknown pass is refused', fake.json.valid === false, fake.json.reason);

  const gateResult = await call('POST', '/v1/gate/results', {
    auth: apiKey,
    body: { passId: gate.json.passId, granted: true, faceScore: 0.81 },
  });
  check('gate result recorded', gateResult.json.ok === true,
    `${gateResult.json.monitors} monitor(s) open`);

  // ---- 8. Batch verification (sandbox /lag/batch) ----
  console.log('\n8. HR batch check');
  const batch = await call('POST', '/v1/verify/batch', {
    auth: apiKey,
    body: {
      items: [
        { lagId: RESIDENT.lagId, expectedName: RESIDENT.name },
        { lagId: UNLINKED, expectedName: 'Wrong Person' },
        { lagId: 'LAG999999' },
        { lagId: 'not-an-id' },
      ],
    },
  });
  const rows = batch.json.results || [];
  check('batch answered all 4', rows.length === 4,
    JSON.stringify(batch.json.summary));
  check('verified resident with matching name',
    rows[0]?.verified === true && rows[0]?.nameMatch === true);
  check('wrong name is flagged', rows[1]?.nameMatch === false);
  check('unknown LagID is NOT_FOUND', rows[2]?.status === 'NOT_FOUND');
  check('bad format is INVALID_ID', rows[3]?.status === 'INVALID_ID');
  check('no names or phones in batch answers',
    !JSON.stringify(rows).includes(RESIDENT.phone));

  const stats = await call('GET', '/v1/stats', { auth: apiKey });
  check('stats include the batch', stats.json.batchChecks >= 4,
    JSON.stringify(stats.json));

  console.log(`\n${passed} passed, ${failed} failed\n`);
  process.exit(failed ? 1 : 0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
