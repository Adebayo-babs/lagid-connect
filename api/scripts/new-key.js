// Makes a new API key for a business and prints it once.
//
//   npm run key                         (Ikeja Heights Estate)
//   npm run key -- "Lagos Tech Fest"

import mongoose from 'mongoose';
import { connectDb } from '../src/db.js';
import { Business, ApiKey } from '../src/models/index.js';
import { newApiKey } from '../src/lib/crypto.js';

const name = process.argv[2] || 'Ikeja Heights Estate';

async function main() {
  await connectDb();
  const biz = await Business.findOne({ name });
  if (!biz) {
    const all = await Business.find().select('name').lean();
    console.log(`No business called "${name}". You have:`);
    all.forEach((b) => console.log(`  ${b.name}`));
    process.exit(1);
  }

  const { key, prefix, keyHash } = newApiKey('test');
  await ApiKey.create({ businessId: biz._id, keyHash, prefix });

  console.log(`\nNew API key for ${biz.name}:\n`);
  console.log(`  ${key}\n`);
  console.log('Shown only once. Copy it now.\n');
  await mongoose.disconnect();
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
