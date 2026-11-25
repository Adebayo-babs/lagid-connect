import { NextResponse } from 'next/server';
import { apiFetch } from '@/lib/api';
import { showcaseKey } from '@/lib/showcase';

// The estate's own website calling LagID Connect from its
// server. Uses SHOWCASE_API_KEY, or the console sign-in key.

export async function POST(request) {
  const key = await showcaseKey();
  if (!key) {
    return NextResponse.json(
      { error: { message: 'Set SHOWCASE_API_KEY or sign in to the console first.' } },
      { status: 401 }
    );
  }
  const { lagId, fullName, flat } = await request.json().catch(() => ({}));

  const res = await apiFetch('/v1/requests', {
    key,
    method: 'POST',
    body: {
      lagId,
      purpose: `Tenancy application, ${flat || 'Flat 4B'}`,
      claims: ['lagos_resident', 'name_match', 'age_over_18'],
      requiredClaims: ['lagos_resident', 'name_match'],
      expectedName: fullName,
      reference: `IH-${Date.now().toString(36).toUpperCase()}`,
    },
  });
  return NextResponse.json(res.data, { status: res.status });
}
