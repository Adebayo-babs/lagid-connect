import { NextResponse } from 'next/server';
import { createRemoteJWKSet, jwtVerify } from 'jose';
import { API_URL } from '@/lib/api';

// Checks a claims token the way any business would: with
// the public keys at /.well-known/jwks.json. Nothing secret.
const jwks = createRemoteJWKSet(new URL('/.well-known/jwks.json', API_URL));

export async function POST(request) {
  const { token } = await request.json().catch(() => ({}));
  try {
    const { payload, protectedHeader } = await jwtVerify(String(token || ''), jwks);
    return NextResponse.json({
      valid: true,
      alg: protectedHeader.alg,
      kid: protectedHeader.kid,
      payload,
    });
  } catch (err) {
    return NextResponse.json({ valid: false, error: err.code || err.message });
  }
}
