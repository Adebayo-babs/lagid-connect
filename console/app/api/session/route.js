import { NextResponse } from 'next/server';
import { apiFetch, KEY_COOKIE } from '@/lib/api';

// Sign in: check the key against the API, then keep it in
// an httpOnly cookie the browser's scripts can't read.
export async function POST(request) {
  const { key } = await request.json().catch(() => ({}));
  const clean = String(key || '').trim();

  if (!/^lc_(test|live)_\S+$/.test(clean)) {
    return NextResponse.json(
      { error: 'That doesn’t look like a LagID Connect key.' },
      { status: 400 }
    );
  }

  const me = await apiFetch('/v1/me', { key: clean }).catch(() => null);
  if (!me) {
    return NextResponse.json(
      { error: 'Can’t reach the Connect API.' },
      { status: 502 }
    );
  }
  if (!me.ok) {
    return NextResponse.json(
      { error: 'That key isn’t valid.' },
      { status: 401 }
    );
  }

  const res = NextResponse.json({ business: me.data });
  res.cookies.set(KEY_COOKIE, clean, {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: 60 * 60 * 12,
  });
  return res;
}

// Sign out
export async function DELETE() {
  const res = NextResponse.json({ ok: true });
  res.cookies.delete(KEY_COOKIE);
  return res;
}
