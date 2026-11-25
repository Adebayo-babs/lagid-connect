// Shared helpers for consent requests.

import { Request } from '../models/index.js';
import { CLAIM_TYPES } from './claims.js';
import { logEvent, EVENT_TYPES } from './events.js';
import { sendWebhook } from './webhooks.js';

export const DEFAULT_TTL_MIN = 10;
export const MAX_TTL_MIN = 60;

// Pending requests past their expiry are marked expired
// when someone looks at them (no background job needed).
export async function expireIfDue(request, business) {
  if (request.status !== 'pending') return request;
  if (request.expiresAt > new Date()) return request;

  const updated = await Request.findOneAndUpdate(
    { _id: request._id, status: 'pending' },
    { status: 'expired', decidedAt: new Date() },
    { new: true }
  );
  if (!updated) return Request.findById(request._id);

  await logEvent({
    type: EVENT_TYPES.requestExpired,
    actor: 'system',
    businessId: updated.businessId,
    residentId: updated.residentId,
    requestId: updated._id,
    result: 'expired',
  });
  sendWebhook(business, EVENT_TYPES.requestExpired, {
    requestId: String(updated._id),
    reference: updated.reference || null,
  });
  return updated;
}

// What a business sees
export function businessView(r) {
  return {
    id: String(r._id),
    status: r.status,
    purpose: r.purpose,
    reference: r.reference || null,
    claims: r.claims,
    requiredClaims: r.requiredClaims,
    approvedClaims: r.status === 'approved' ? r.approvedClaims : [],
    token: r.status === 'approved' ? r.claimsToken : null,
    createdAt: r.createdAt,
    expiresAt: r.expiresAt,
    decidedAt: r.decidedAt || null,
  };
}

// What the resident's wallet sees
export function walletView(r, business) {
  return {
    id: String(r._id),
    status: r.status,
    purpose: r.purpose,
    business: business
      ? {
          id: String(business._id),
          name: business.name,
          category: business.category,
          verified: business.verified,
          logoUrl: business.logoUrl || null,
        }
      : null,
    claims: r.claims.map((id) => ({
      id,
      label: CLAIM_TYPES[id] || id,
      required: r.requiredClaims.includes(id),
    })),
    approvedClaims: r.approvedClaims || [],
    createdAt: r.createdAt,
    expiresAt: r.expiresAt,
    decidedAt: r.decidedAt || null,
  };
}
