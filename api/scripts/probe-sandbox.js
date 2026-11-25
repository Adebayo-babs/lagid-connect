// Calls all three sandbox endpoints and saves the raw
// responses to api/samples/ so we can see real fields.
//
// Usage:
//   npm run probe                 (uses the first sample LagID)
//   npm run probe -- LAG123...    (look up a specific one)

import fs from 'node:fs/promises';
import path from 'node:path';
import { config } from '../src/config.js';
import {
  rawSandboxCall,
  SANDBOX_PATHS,
} from '../src/lib/sandbox.js';

const outDir = path.resolve('samples');

async function save(name, data) {
  await fs.mkdir(outDir, { recursive: true });
  const file = path.join(outDir, `${name}.json`);
  await fs.writeFile(file, JSON.stringify(data, null, 2));
  console.log(`  saved ${file}`);
}

function firstLagId(body) {
  const text = JSON.stringify(body || {});
  const match = text.match(/"(?:lagId|lag_id|lagID|LAGID)"\s*:\s*"([^"]+)"/);
  return match?.[1];
}

async function main() {
  if (config.sandbox.mock) {
    console.log('SANDBOX_MOCK=true: set it to false to probe.');
    return;
  }

  console.log(`Probing ${config.sandbox.baseUrl}\n`);

  const listPath = SANDBOX_PATHS.list(0, 5);
  console.log(`GET ${listPath}`);
  const list = await rawSandboxCall(listPath);
  console.log(`  status ${list.status}`);
  await save('list', list);

  const lagId = process.argv[2] || firstLagId(list.body);
  if (!lagId) {
    console.log('\nNo LagID found. Pass one: npm run probe -- LAG...');
    return;
  }

  console.log(`\nGET ${SANDBOX_PATHS.lookup(lagId)}`);
  const one = await rawSandboxCall(SANDBOX_PATHS.lookup(lagId));
  console.log(`  status ${one.status}`);
  await save('lookup', one);

  console.log(`\nPOST ${SANDBOX_PATHS.batch}`);
  const batch = await rawSandboxCall(SANDBOX_PATHS.batch, {
    method: 'POST',
    body: JSON.stringify({ lag_ids: [lagId, 'LAG999999', 'not-an-id'] }),
  });
  console.log(`  status ${batch.status}`);
  await save('batch', batch);

  console.log('\nDone. Open api/samples/ and check the field names.');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
