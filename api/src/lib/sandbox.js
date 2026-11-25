// LagID sandbox client.
// Docs: https://hackathon.dev.identikosharedsolutions.com/documentation
// The sandbox key stays on the server: phones and
// browsers only ever talk to the Connect API.

import { config } from '../config.js';
import { HttpError } from '../middleware/errors.js';
import { mockResidents } from './mockResidents.js';
import { normalizeResident, unwrap } from './normalize.js';

const PATHS = {
  list: (page, limit) =>
    `/api/v1/identity/verify?page=${page}&limit=${limit}`,
  lookup: (lagId) =>
    `/api/v1/identity/lag/${encodeURIComponent(lagId)}`,
  batch: '/api/v1/identity/lag/batch',
};

const sameId = (a, b) =>
  String(a).trim().toUpperCase() === String(b).trim().toUpperCase();

// ---------- small in-memory cache ----------

const cache = new Map();

function cacheGet(key) {
  const hit = cache.get(key);
  if (!hit) return undefined;
  if (Date.now() > hit.expires) {
    cache.delete(key);
    return undefined;
  }
  return hit.value;
}

function cacheSet(key, value) {
  const ttl = config.sandbox.cacheTtl * 1000;
  cache.set(key, { value, expires: Date.now() + ttl });
}

export function clearSandboxCache() {
  cache.clear();
}

// ---------- HTTP ----------

async function call(path, options = {}) {
  const { baseUrl, apiKey } = config.sandbox;
  if (!baseUrl || !apiKey) {
    throw new HttpError(
      503,
      'sandbox_not_configured',
      'Set SANDBOX_BASE_URL and SANDBOX_API_KEY, ' +
        'or use SANDBOX_MOCK=true'
    );
  }

  let res;
  try {
    res = await fetch(baseUrl + path, {
      ...options,
      headers: {
        'X-API-Key': apiKey,
        Accept: 'application/json',
        ...(options.body
          ? { 'Content-Type': 'application/json' }
          : {}),
        ...options.headers,
      },
      signal: AbortSignal.timeout(15000),
    });
  } catch (err) {
    throw new HttpError(
      502, 'sandbox_unreachable', `Sandbox unreachable: ${err.message}`
    );
  }

  const text = await res.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = { raw: text };
  }

  return { status: res.status, ok: res.ok, body };
}

// ---------- public API ----------

// Look up one resident. Returns null when not found
// (the sandbox returns 404 for missing or inactive).
export async function lookupResident(lagId) {
  const id = String(lagId || '').trim();
  if (!id) throw new HttpError(400, 'bad_lag_id', 'LagID is required');

  const key = `lag:${id.toUpperCase()}`;
  const cached = cacheGet(key);
  if (cached !== undefined) return cached;

  let resident;
  if (config.sandbox.mock) {
    const raw = mockResidents.find((r) => sameId(r.lag_id, id));
    resident = raw ? normalizeResident(raw) : null;
  } else {
    const res = await call(PATHS.lookup(id));
    if (res.status === 404 || res.status === 400) {
      resident = null;
    } else if (!res.ok) {
      throw sandboxError(res);
    } else {
      resident = normalizeResident(res.body);
    }
  }

  cacheSet(key, resident);
  return resident;
}

// Look up 1 to 100 LagIDs.
// Returns { results: [{ lagId, status, resident }], found,
// missing, invalid } where status is FOUND, NOT_FOUND or
// INVALID_ID, in the order given.
export async function lookupBatch(lagIds) {
  const ids = (lagIds || []).map((x) => String(x ?? '').trim());
  if (ids.length === 0) {
    throw new HttpError(400, 'empty_batch', 'No LagIDs given');
  }
  if (ids.length > 100) {
    throw new HttpError(
      400, 'batch_too_large', 'At most 100 LagIDs per batch'
    );
  }

  let results;
  if (config.sandbox.mock) {
    results = ids.map((id) => {
      if (!/^LAG\d+$/i.test(id)) {
        return { lagId: id, status: 'INVALID_ID', resident: null };
      }
      const raw = mockResidents.find((r) => sameId(r.lag_id, id));
      return raw
        ? { lagId: id, status: 'FOUND', resident: normalizeResident(raw) }
        : { lagId: id, status: 'NOT_FOUND', resident: null };
    });
  } else {
    const res = await call(PATHS.batch, {
      method: 'POST',
      body: JSON.stringify({ lag_ids: ids }),
    });
    if (!res.ok) throw sandboxError(res);

    const list = findArray(unwrap(res.body), ['results', 'items', 'content']);
    results = list.map((item) => ({
      lagId: item.lag_id ?? item.lagId,
      status: item.status,
      message: item.message,
      resident: item.identity ? normalizeResident(item.identity) : null,
    }));
  }

  return {
    results,
    found: results.filter((r) => r.status === 'FOUND').map((r) => r.resident),
    missing: results.filter((r) => r.status === 'NOT_FOUND').map((r) => r.lagId),
    invalid: results.filter((r) => r.status === 'INVALID_ID').map((r) => r.lagId),
  };
}

// All sample residents, following the pages
export async function listSampleResidents({ maxPages = 10 } = {}) {
  if (config.sandbox.mock) {
    return mockResidents.map(normalizeResident);
  }

  const out = [];
  const limit = 100;
  for (let page = 0; page < maxPages; page += 1) {
    const res = await call(PATHS.list(page, limit));
    if (!res.ok) throw sandboxError(res);
    const data = unwrap(res.body) || {};
    for (const raw of data.content || []) {
      out.push(normalizeResident(raw));
    }
    if (!data.totalPages || page + 1 >= data.totalPages) break;
  }
  return out.filter(Boolean);
}

// Raw access, used by the probe script
export async function rawSandboxCall(path, options) {
  return call(path, options);
}

export { PATHS as SANDBOX_PATHS };

// ---------- helpers ----------

function findArray(value, keys) {
  if (Array.isArray(value)) return value;
  if (value && typeof value === 'object') {
    for (const key of keys) {
      if (Array.isArray(value[key])) return value[key];
    }
    // Fall back to the first array property
    const first = Object.values(value).find(Array.isArray);
    if (first) return first;
  }
  return [];
}

function sandboxError(res) {
  const status = res.status >= 500 || res.status === 401 || res.status === 403
    ? 502
    : 400;
  const message = res.body?.message || `Sandbox returned ${res.status}`;
  return new HttpError(status, 'sandbox_error', message, {
    sandboxStatus: res.status,
  });
}
