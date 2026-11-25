// One append-only log for both the business audit log
// and the resident's activity log.

import { Event } from '../models/index.js';

export const EVENT_TYPES = {
  walletLinked: 'wallet.linked',
  requestCreated: 'request.created',
  requestApproved: 'request.approved',
  requestDeclined: 'request.declined',
  requestExpired: 'request.expired',
  grantRevoked: 'grant.revoked',
  credentialIssued: 'credential.issued',
  gateResult: 'gate.result',
  batchVerified: 'batch.verified',
};

export async function logEvent(data) {
  try {
    return await Event.create({ ...data, at: new Date() });
  } catch (err) {
    // Logging must never break the main flow
    console.error('[events] failed to log', data.type, err.message);
    return null;
  }
}
