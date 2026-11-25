'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';

export default function LoginPage() {
  const router = useRouter();
  const [key, setKey] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    const res = await fetch('/api/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key }),
    });
    const data = await res.json().catch(() => ({}));
    setBusy(false);
    if (!res.ok) return setError(data.error || 'Sign-in failed');
    router.replace('/');
    router.refresh();
  }

  return (
    <div className="center">
      <form className="login stack" onSubmit={submit}>
        <div>
          <div className="eyebrow">LagID Connect</div>
          <h1>Business Console</h1>
          <p className="muted" style={{ marginTop: 8 }}>
            Sign in with your business API key.
          </p>
        </div>
        <label className="field">
          API key
          <input
            type="password"
            placeholder="lc_test_..."
            value={key}
            onChange={(e) => setKey(e.target.value)}
            autoComplete="off"
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="btn" type="submit" disabled={busy || !key}>
          {busy ? 'Checking…' : 'Sign in'}
        </button>
        <p className="muted small">
          Get a key with <code>npm run key</code> in the API folder.
        </p>
      </form>
    </div>
  );
}
