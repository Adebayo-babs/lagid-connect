// Server-only helpers for calling the Connect API.
// The business API key lives in an httpOnly cookie and is
// only ever read on the server.

import { cookies } from 'next/headers';

export const API_URL = (process.env.API_URL || 'http://localhost:4000')
  .replace(/\/+$/, '');

export const KEY_COOKIE = 'lc_key';

export async function getKey() {
  const store = await cookies();
  return store.get(KEY_COOKIE)?.value || null;
}

export async function apiFetch(path, { key, method = 'GET', body } = {}) {
  const res = await fetch(API_URL + path, {
    method,
    cache: 'no-store',
    headers: {
      ...(key ? { Authorization: `Bearer ${key}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  return { ok: res.ok, status: res.status, data };
}

// Paths the browser may reach through /api/proxy
const ALLOWED = [
  /^requests$/,
  /^requests\/[a-f0-9]{24}$/,
  /^events$/,
  /^stats$/,
  /^me$/,
  /^verify\/batch$/,
];

export function proxyAllowed(path) {
  return ALLOWED.some((re) => re.test(path));
}
