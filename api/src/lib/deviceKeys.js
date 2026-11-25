// Checks on device public keys and device signatures.

import { importJWK, compactVerify } from 'jose';
import { HttpError } from '../middleware/errors.js';

// Accept only a public P-256 key, nothing private
export function cleanDevicePublicKey(jwk) {
  if (
    !jwk ||
    jwk.kty !== 'EC' ||
    jwk.crv !== 'P-256' ||
    typeof jwk.x !== 'string' ||
    typeof jwk.y !== 'string'
  ) {
    throw new HttpError(
      400,
      'bad_public_key',
      'publicKey must be a P-256 EC JWK'
    );
  }
  if (jwk.d) {
    throw new HttpError(
      400,
      'bad_public_key',
      'Send the public key only, never the private key'
    );
  }
  return { kty: 'EC', crv: 'P-256', x: jwk.x, y: jwk.y };
}

// Verify a compact JWS signed by the device.
// Returns the parsed JSON payload.
export async function verifyDeviceSignature(device, jws, maxAgeSec = 300) {
  let payload;
  try {
    const key = await importJWK(device.publicKey, 'ES256');
    const result = await compactVerify(String(jws || ''), key);
    payload = JSON.parse(new TextDecoder().decode(result.payload));
  } catch {
    throw new HttpError(
      401,
      'bad_signature',
      'Decision signature not valid'
    );
  }

  const now = Math.floor(Date.now() / 1000);
  if (
    typeof payload.iat !== 'number' ||
    payload.iat > now + 30 ||
    now - payload.iat > maxAgeSec
  ) {
    throw new HttpError(401, 'stale_signature', 'Signature too old');
  }
  return payload;
}
