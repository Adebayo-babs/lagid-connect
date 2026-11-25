// Seeds a demo business and prints its API key.
// Needs MONGODB_URI. Safe to run more than once.

import mongoose from 'mongoose';
import { config } from '../src/config.js';
import { connectDb } from '../src/db.js';
import { Business, ApiKey } from '../src/models/index.js';
import { newApiKey } from '../src/lib/crypto.js';
import { listSampleResidents } from '../src/lib/sandbox.js';

const DEMO_BUSINESSES = [
  { name: 'Ikeja Heights Estate', category: 'estate' },
  { name: 'HireRight Onboarding', category: 'employer' },
  { name: 'Lagos Tech Fest', category: 'event' },
];

async function main() {
  if (!config.mongoUri) {
    console.error('Set MONGODB_URI in api/.env first.');
    process.exit(1);
  }
  await connectDb();

  for (const data of DEMO_BUSINESSES) {
    let biz = await Business.findOne({ name: data.name });
    if (!biz) {
      biz = await Business.create({ ...data, verified: true });
      console.log(`created business: ${biz.name}`);
    } else {
      console.log(`exists: ${biz.name}`);
    }

    const hasKey = await ApiKey.exists({
      businessId: biz._id,
      revokedAt: null,
    });
    if (!hasKey) {
      const { key, prefix, keyHash } = newApiKey('test');
      await ApiKey.create({ businessId: biz._id, keyHash, prefix });
      console.log(`  API key (shown once): ${key}`);
    }
  }

  const residents = await listSampleResidents();
  console.log(`\nSandbox has ${residents.length} sample residents:`);
  for (const r of residents.slice(0, 10)) {
    console.log(`  ${r.lagId}  ${r.fullName}  (${r.status})`);
  }

  await mongoose.disconnect();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
