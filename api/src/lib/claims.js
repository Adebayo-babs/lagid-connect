// Turns a resident record into the claims a business
// asked for. Derived yes/no answers wherever possible.

import { HttpError } from '../middleware/errors.js';

export const CLAIM_TYPES = {
  lagos_resident: 'Is a verified Lagos resident',
  name_match: 'Name matches the one the business supplied',
  full_name: 'Full name',
  age_over_18: 'Is 18 or older',
  lga: 'Local government area',
  photo: 'Face photo, for in-person checks only',
  phone_verified: 'Phone number on record was verified',
};

export function assertKnownClaims(claims) {
  const unknown = (claims || []).filter((c) => !CLAIM_TYPES[c]);
  if (unknown.length) {
    throw new HttpError(
      400,
      'unknown_claims',
      `Unknown claims: ${unknown.join(', ')}`,
      { allowed: Object.keys(CLAIM_TYPES) }
    );
  }
}

export function ageOn(dateOfBirth, today = new Date()) {
  const dob = new Date(dateOfBirth);
  if (Number.isNaN(dob.getTime())) return null;
  let age = today.getFullYear() - dob.getFullYear();
  const m = today.getMonth() - dob.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < dob.getDate())) {
    age -= 1;
  }
  return age;
}

function tokens(name) {
  return String(name || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z\s-]/g, ' ')
    .split(/[\s-]+/)
    .filter((t) => t.length > 1);
}

// Loose name match: order-independent, ignores middle
// names. True when first and last names both appear.
export function nameMatches(resident, expected) {
  const given = new Set(tokens(expected));
  if (given.size === 0) return false;
  const first = tokens(resident.firstName)[0];
  const last = tokens(resident.lastName)[0];
  if (first && last) return given.has(first) && given.has(last);
  const all = tokens(resident.fullName);
  return all.length > 0 && all.every((t) => given.has(t));
}

// claims: ['lagos_resident', 'name_match', ...]
// context: { expectedName, phoneVerified }
export function deriveClaims(resident, claims, context = {}) {
  assertKnownClaims(claims);
  // A record counts only if the sandbox returned it (active)
  // and it is a verified sample
  const active =
    !!resident &&
    String(resident.status).toLowerCase() === 'active' &&
    resident.verified !== false;
  const out = {};

  for (const claim of claims) {
    switch (claim) {
      case 'lagos_resident':
        out.lagos_resident = active;
        break;
      case 'name_match':
        out.name_match = active &&
          nameMatches(resident, context.expectedName);
        break;
      case 'full_name':
        out.full_name = resident?.fullName ?? null;
        break;
      case 'age_over_18': {
        const age = ageOn(resident?.dateOfBirth);
        out.age_over_18 = age === null ? null : age >= 18;
        break;
      }
      case 'lga':
        out.lga = resident?.lga ?? null;
        break;
      case 'photo':
        out.photo = resident?.photoUrl ?? null;
        break;
      case 'phone_verified':
        out.phone_verified = !!context.phoneVerified;
        break;
      default:
        break;
    }
  }
  return out;
}
