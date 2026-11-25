// Sends a verification request to a linked wallet, as
// Ikeja Heights Estate. Use it to test the phone app.
//
//   npm run ask -- LAG100002       (the LagID you linked)
//
// Then watch the result:
//   npm run ask -- --status <requestId>

import mongoose from 'mongoose';
import { connectDb } from '../src/db.js';
import { Business, ApiKey } from '../src/models/index.js';
import { newApiKey } from '../src/lib/crypto.js';
import { decodeJwt } from 'jose';
import { lookupResident } from '../src/lib/sandbox.js';

const API = process.env.API_URL || 'http://localhost:4000';

async function apiKey() {
  if (process.env.DEMO_API_KEY) return process.env.DEMO_API_KEY;
  await connectDb();
  const biz = await Business.findOne({ name: 'Ikeja Heights Estate' });
  if (!biz) throw new Error('Run `npm run seed` first');
  const { key, prefix, keyHash } = newApiKey('test');
  await ApiKey.create({ businessId: biz._id, keyHash, prefix });
  await mongoose.disconnect();
  return key;
}

async function main() {
  const key = await apiKey();
  const args = process.argv.slice(2);

  if (args[0] === '--status') {
    const res = await fetch(`${API}/v1/requests/${args[1]}`, {
      headers: { Authorization: `Bearer ${key}` },
    });
    const { request, error } = await res.json();
    if (error) return console.log(error);
    console.log(`status: ${request.status}`);
    if (request.token) {
      console.log('claims:', decodeJwt(request.token).claims);
    }
    return;
  }

  const lagId = args[0];
  if (!lagId) {
    console.log('Pass the LagID you linked: npm run ask -- LAG100002');
    return;
  }

  // Use the resident's real name for the name_match claim
  // Dev route locally; direct sandbox lookup when deployed
  const who = await fetch(`${API}/v1/dev/lookup/${lagId}`);
  const resident = who.ok
    ? (await who.json()).resident
    : await lookupResident(lagId);
  if (!resident) return console.log(`No sample resident ${lagId}`);
  const expectedName = `${resident.firstName} ${resident.lastName}`;

  const res = await fetch(`${API}/v1/requests`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${key}`,
    },
    body: JSON.stringify({
      lagId,
      claims: ['lagos_resident', 'name_match', 'age_over_18', 'full_name'],
      requiredClaims: ['lagos_resident', 'name_match'],
      purpose: 'Tenancy application, Flat 4B',
      expectedName,
      reference: 'APP-4B-001',
    }),
  });
  const body = await res.json();
  if (!res.ok) return console.log(body.error);

  console.log(`Sent request ${body.request.id} to ${lagId}.`);
  console.log('Open the wallet: it shows up within 4 seconds.');
  console.log(`Check it: npm run ask -- --status ${body.request.id}`);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
