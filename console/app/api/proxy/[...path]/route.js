import { NextResponse } from 'next/server';
import { apiFetch, getKey, proxyAllowed } from '@/lib/api';

// Browser -> this route -> Connect API, with the key added
// on the server. Only the paths the console uses are open.

async function forward(request, { params }) {
  const { path = [] } = await params;
  const joined = path.join('/');

  if (!proxyAllowed(joined)) {
    return NextResponse.json({ error: { message: 'Not allowed' } }, { status: 404 });
  }
  const key = await getKey();
  if (!key) {
    return NextResponse.json({ error: { message: 'Signed out' } }, { status: 401 });
  }

  const search = new URL(request.url).search;
  const body = request.method === 'POST'
    ? await request.json().catch(() => ({}))
    : undefined;

  try {
    const res = await apiFetch(`/v1/${joined}${search}`, {
      key,
      method: request.method,
      body,
    });
    return NextResponse.json(res.data, { status: res.status });
  } catch {
    return NextResponse.json(
      { error: { message: 'Can’t reach the Connect API' } },
      { status: 502 }
    );
  }
}

export const GET = forward;
export const POST = forward;
