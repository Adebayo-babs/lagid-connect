// Wallet API auth.
// The phone signs a short-lived JWT with its own device
// key (ES256, private key in the Android Keystore):
//
//   header:  { alg: 'ES256', kid: '<deviceId>' }
//   payload: { aud: 'lagid-connect-wallet', iat, exp }
//
// and sends it as Authorization: Bearer <jwt>.
// We check it against the public key saved at link time.

import { decodeProtectedHeader, importJWK, jwtVerify } from 'jose';
import mongoose from 'mongoose';
import { Device, Resident } from '../models/index.js';
import { HttpError, wrap } from './errors.js';

export const WALLET_AUDIENCE = 'lagid-connect-wallet';
const MAX_AGE = '5m';

export const walletAuth = wrap(async (req, res, next) => {
  const header = req.get('authorization') || '';
  const token = header.replace(/^Bearer\s+/i, '');
  if (!token || token === header) {
    throw new HttpError(401, 'missing_device_token', 'Device token required');
  }

  let kid;
  try {
    kid = decodeProtectedHeader(token).kid;
  } catch {
    throw new HttpError(401, 'bad_device_token', 'Malformed device token');
  }
  if (!kid || !mongoose.isValidObjectId(kid)) {
    throw new HttpError(401, 'bad_device_token', 'Token has no device id');
  }

  const device = await Device.findById(kid);
  if (!device || device.status !== 'active') {
    throw new HttpError(401, 'device_not_active', 'Device is not linked');
  }

  try {
    const key = await importJWK(device.publicKey, 'ES256');
    await jwtVerify(token, key, {
      audience: WALLET_AUDIENCE,
      maxTokenAge: MAX_AGE,
      clockTolerance: 30,
    });
  } catch {
    throw new HttpError(401, 'bad_device_token', 'Device token not valid');
  }

  const resident = await Resident.findById(device.residentId);
  if (!resident) {
    throw new HttpError(401, 'device_not_active', 'Resident not found');
  }

  req.device = device;
  req.resident = resident;
  next();
});
