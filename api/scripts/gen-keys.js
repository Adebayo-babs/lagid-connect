// Generates a permanent ES256 signing key.
// Paste the two printed lines into api/.env
// (and into Render's environment settings).

import { generateKeyPair, exportJWK } from 'jose';

const kid = `lc-${new Date().toISOString().slice(0, 10)}`;
const { privateKey } = await generateKeyPair('ES256', {
  extractable: true,
});
const jwk = { ...(await exportJWK(privateKey)), kid, alg: 'ES256' };

console.log('\n# Add these to api/.env\n');
console.log(`SIGNING_KID=${kid}`);
console.log(`SIGNING_PRIVATE_JWK=${JSON.stringify(jwk)}`);
console.log('\nKeep SIGNING_PRIVATE_JWK secret. Never commit it.\n');
