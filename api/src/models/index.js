// All eight collections from the spec.
// The full resident record is never stored here:
// the sandbox stays the source of truth.

import mongoose from 'mongoose';

const { Schema, model, Types } = mongoose;
const ref = (name) => ({ type: Types.ObjectId, ref: name });
const opts = { timestamps: true };

// ---------- residents ----------
const residentSchema = new Schema(
  {
    lagIdHash: { type: String, required: true, unique: true },
    lagIdLast4: String,
    displayName: String,
    phoneMasked: String,
    photoRef: String, // encrypted cached photo (later)
    // SHA-256 of the face template enrolled at linking.
    // The template itself stays on the resident's phone.
    faceHash: String,
    linkedAt: Date,
  },
  opts
);

// ---------- devices ----------
const deviceSchema = new Schema(
  {
    residentId: { ...ref('Resident'), required: true, index: true },
    publicKey: { type: Object, required: true }, // P-256 JWK
    platform: { type: String, default: 'android' },
    pushToken: String,
    status: {
      type: String,
      enum: ['active', 'retired'],
      default: 'active',
    },
  },
  opts
);

// ---------- businesses ----------
const businessSchema = new Schema(
  {
    name: { type: String, required: true },
    logoUrl: String,
    category: {
      type: String,
      enum: ['landlord', 'estate', 'employer', 'event', 'other'],
      default: 'other',
    },
    verified: { type: Boolean, default: false },
    webhookUrl: String,
    webhookSecret: String,
    plan: { type: String, default: 'sandbox' },
  },
  opts
);

// ---------- apiKeys ----------
const apiKeySchema = new Schema(
  {
    businessId: { ...ref('Business'), required: true, index: true },
    keyHash: { type: String, required: true, unique: true },
    prefix: String,
    scopes: { type: [String], default: ['requests', 'verify'] },
    lastUsedAt: Date,
    revokedAt: Date,
  },
  opts
);

// ---------- requests (consent) ----------
const requestSchema = new Schema(
  {
    businessId: { ...ref('Business'), required: true },
    residentId: { ...ref('Resident'), required: true },
    claims: { type: [String], required: true },
    requiredClaims: { type: [String], default: [] },
    purpose: { type: String, required: true },
    // Name the business expects, for the name_match claim
    expectedName: String,
    reference: String, // the business's own reference
    status: {
      type: String,
      enum: ['pending', 'approved', 'declined', 'expired'],
      default: 'pending',
    },
    approvedClaims: [String],
    residentSignature: String,
    claimsToken: String,
    expiresAt: { type: Date, required: true },
    decidedAt: Date,
  },
  opts
);
requestSchema.index({ residentId: 1, status: 1 });

// ---------- grants ----------
const grantSchema = new Schema(
  {
    businessId: { ...ref('Business'), required: true },
    residentId: { ...ref('Resident'), required: true },
    claims: [String],
    status: {
      type: String,
      enum: ['active', 'revoked'],
      default: 'active',
    },
    revokedAt: Date,
  },
  opts
);
grantSchema.index({ residentId: 1, businessId: 1 });

// ---------- credentials ----------
const credentialSchema = new Schema(
  {
    residentId: { ...ref('Resident'), required: true, index: true },
    deviceId: { ...ref('Device'), required: true },
    type: { type: String, required: true },
    claims: Object,
    jti: { type: String, required: true, unique: true },
    issuedAt: Date,
    expiresAt: Date,
    status: {
      type: String,
      enum: ['active', 'revoked', 'expired'],
      default: 'active',
    },
  },
  opts
);

// ---------- events (audit + activity log) ----------
const eventSchema = new Schema({
  type: { type: String, required: true },
  actor: String, // 'resident' | 'business' | 'verifier' | 'system'
  businessId: ref('Business'),
  residentId: ref('Resident'),
  requestId: ref('Request'),
  result: String,
  reason: String,
  meta: Object,
  at: { type: Date, default: Date.now },
});
eventSchema.index({ residentId: 1, at: -1 });
eventSchema.index({ businessId: 1, at: -1 });

// ---------- presentations (gate passes) ----------
// A single-use code the wallet shows as a wave QR.
// Lives 30 seconds; Mongo deletes it soon after.
const presentationSchema = new Schema(
  {
    code: { type: String, required: true, unique: true },
    residentId: { ...ref('Resident'), required: true },
    deviceId: ref('Device'),
    displayName: String,
    lagIdLast4: String,
    claims: Object,
    // Held only while the pass is live, for the gate's face match
    faceTemplate: String,
    expiresAt: { type: Date, required: true },
    usedAt: Date,
    usedBy: ref('Business'),
  },
  opts
);
presentationSchema.index(
  { expiresAt: 1 },
  { expireAfterSeconds: 120 }
);

export const Resident = model('Resident', residentSchema);
export const Presentation = model('Presentation', presentationSchema);
export const Device = model('Device', deviceSchema);
export const Business = model('Business', businessSchema);
export const ApiKey = model('ApiKey', apiKeySchema);
export const Request = model('Request', requestSchema);
export const Grant = model('Grant', grantSchema);
export const Credential = model('Credential', credentialSchema);
export const Event = model('Event', eventSchema);
