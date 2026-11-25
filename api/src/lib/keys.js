// ES256 signing for claims tokens and credentials.
// The private key comes from SIGNING_PRIVATE_JWK.
// In development, if none is set, a temporary key is
// generated on start (tokens won't survive a restart).

import {
  importJWK,
  exportJWK,
  generateKeyPair,
  SignJWT,
  jwtVerify,
  createLocalJWKSet,
} from 'jose';
import { config, isDev } from '../config.js';

const ALG = 'ES256';

let privateKey;
let publicJwk;
let kid;

export async function loadSigningKey() {
  if (config.signing.privateJwk) {
    const jwk = JSON.parse(config.signing.privateJwk);
    kid = config.signing.kid || jwk.kid || 'lc-1';
    privateKey = await importJWK(jwk, ALG);
    const { d, ...pub } = jwk; // drop the private part
    publicJwk = { ...pub, kid, alg: ALG, use: 'sig' };
    console.log(`[keys] loaded signing key ${kid}`);
    return;
  }

  if (!isDev) {
    throw new Error('SIGNING_PRIVATE_JWK is required in production');
  }

  const pair = await generateKeyPair(ALG, { extractable: true });
  privateKey = pair.privateKey;
  kid = 'dev-temp';
  publicJwk = {
    ...(await exportJWK(pair.publicKey)),
    kid,
    alg: ALG,
    use: 'sig',
  };
  console.warn(
    '[keys] no SIGNING_PRIVATE_JWK: using a temporary dev key. ' +
      'Run `npm run keys` to make a permanent one.'
  );
}

export function jwks() {
  return { keys: publicJwk ? [publicJwk] : [] };
}

// Sign a payload. `ttl` like '1h', '24h', '30s'.
export async function signToken(payload, { audience, subject, ttl, jti }) {
  const jwt = new SignJWT(payload)
    .setProtectedHeader({ alg: ALG, kid, typ: 'JWT' })
    .setIssuer(config.publicUrl)
    .setIssuedAt()
    .setExpirationTime(ttl || '1h');
  if (audience) jwt.setAudience(audience);
  if (subject) jwt.setSubject(subject);
  if (jti) jwt.setJti(jti);
  return jwt.sign(privateKey);
}

// Verify a token we issued. Returns the payload.
export async function verifyToken(token, { audience } = {}) {
  const keySet = createLocalJWKSet(jwks());
  const { payload } = await jwtVerify(token, keySet, {
    issuer: config.publicUrl,
    ...(audience ? { audience } : {}),
  });
  return payload;
}
