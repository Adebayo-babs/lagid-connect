// One-time codes for linking a wallet.
// Kept in memory: fine for one server instance.
// The sandbox has no SMS, so in mock or dev mode the code
// is logged and returned as devCode for the demo.

import crypto from 'node:crypto';
import { sha256 } from './crypto.js';

const TTL_MS = 10 * 60 * 1000;
const MAX_ATTEMPTS = 5;
const pending = new Map();

export function startOtp(data) {
  const linkId = `lnk_${crypto.randomBytes(12).toString('base64url')}`;
  const code = String(crypto.randomInt(0, 1_000_000)).padStart(6, '0');
  pending.set(linkId, {
    ...data,
    codeHash: sha256(code),
    attempts: 0,
    expires: Date.now() + TTL_MS,
  });
  return { linkId, code, expiresIn: TTL_MS / 1000 };
}

// Returns the saved data, or a reason string on failure
export function checkOtp(linkId, code) {
  const entry = pending.get(linkId);
  if (!entry) return { error: 'link_not_found' };
  if (Date.now() > entry.expires) {
    pending.delete(linkId);
    return { error: 'code_expired' };
  }
  entry.attempts += 1;
  if (entry.attempts > MAX_ATTEMPTS) {
    pending.delete(linkId);
    return { error: 'too_many_attempts' };
  }
  if (sha256(String(code || '')) !== entry.codeHash) {
    return { error: 'wrong_code' };
  }
  pending.delete(linkId);
  return { data: entry };
}

// Clear expired entries now and then
setInterval(() => {
  const now = Date.now();
  for (const [id, entry] of pending) {
    if (now > entry.expires) pending.delete(id);
  }
}, 60 * 1000).unref();
