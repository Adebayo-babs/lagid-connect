import crypto from 'node:crypto';
import { config } from '../config.js';

// LagIDs are stored only as a salted hash
export function hashLagId(lagId) {
  return crypto
    .createHmac('sha256', config.hashSalt)
    .update(String(lagId).trim().toUpperCase())
    .digest('hex');
}

// Pairwise subject: the same resident gets a different
// ID for each business, so businesses can't link records.
export function pairwiseSub(residentId, businessId) {
  const h = crypto
    .createHmac('sha256', config.hashSalt)
    .update(`${residentId}:${businessId}`)
    .digest('base64url');
  return `res_${h.slice(0, 22)}`;
}

export function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

export function randomId(prefix, bytes = 12) {
  return `${prefix}_${crypto.randomBytes(bytes).toString('base64url')}`;
}

// Business API key: shown once, stored as a hash
export function newApiKey(mode = 'test') {
  const secret = crypto.randomBytes(24).toString('base64url');
  const key = `lc_${mode}_${secret}`;
  return {
    key,
    prefix: key.slice(0, 12),
    keyHash: sha256(key),
  };
}

export function lastFour(value) {
  const s = String(value || '');
  return s.slice(-4);
}

export function maskPhone(phone) {
  const s = String(phone || '');
  if (s.length < 7) return s;
  return `${s.slice(0, 4)}****${s.slice(-3)}`;
}
