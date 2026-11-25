import { NextResponse } from 'next/server';
import { apiFetch } from '@/lib/api';
import { showcaseKey } from '@/lib/showcase';

export async function GET(request, { params }) {
  const { id } = await params;
  if (!/^[a-f0-9]{24}$/.test(id)) {
    return NextResponse.json({ error: { message: 'Bad id' } }, { status: 400 });
  }
  const key = await showcaseKey();
  const res = await apiFetch(`/v1/requests/${id}`, { key });
  return NextResponse.json(res.data, { status: res.status });
}
