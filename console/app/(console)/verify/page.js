'use client';

import { useCallback, useState } from 'react';
import RequestStatus from '@/components/RequestStatus';
import { CLAIMS } from '@/lib/format';

const ASKABLE = ['lagos_resident', 'name_match', 'age_over_18', 'full_name', 'phone_verified'];

export default function VerifyPage() {
  const [lagId, setLagId] = useState('');
  const [expectedName, setExpectedName] = useState('');
  const [purpose, setPurpose] = useState('');
  const [claims, setClaims] = useState(['lagos_resident', 'name_match']);
  const [required, setRequired] = useState(['lagos_resident']);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [requestId, setRequestId] = useState(null);

  const toggle = (list, setList, id) =>
    setList(list.includes(id) ? list.filter((c) => c !== id) : [...list, id]);

  const fetchStatus = useCallback(
    (id) => fetch(`/api/proxy/requests/${id}`).then((r) => r.json()),
    []
  );

  async function send(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    const res = await fetch('/api/proxy/requests', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        lagId,
        purpose,
        claims,
        requiredClaims: required.filter((c) => claims.includes(c)),
        expectedName: claims.includes('name_match') ? expectedName : undefined,
      }),
    });
    const data = await res.json().catch(() => ({}));
    setBusy(false);
    if (!res.ok) return setError(data.error?.message || 'Request failed');
    setRequestId(data.request.id);
  }

  return (
    <>
      <div className="page-head">
        <div className="eyebrow">Verify a resident</div>
        <h1>Ask a resident to verify with LagID</h1>
        <p>
          The resident gets the request on their phone, chooses what to
          share, and you get a signed answer from the registry.
        </p>
      </div>

      <div className="grid two">
        <form className="card stack" onSubmit={send}>
          <label className="field">
            Resident’s LagID
            <input value={lagId} onChange={(e) => setLagId(e.target.value)} placeholder="LAG100002" />
          </label>
          <label className="field">
            Purpose (the resident sees this)
            <input
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
              placeholder="Tenancy application, Flat 4B"
            />
          </label>

          <div className="stack" style={{ gap: 8 }}>
            <strong>What do you need to know?</strong>
            {ASKABLE.map((id) => (
              <div key={id} className="row" style={{ justifyContent: 'space-between' }}>
                <label className="check">
                  <input
                    type="checkbox"
                    checked={claims.includes(id)}
                    onChange={() => toggle(claims, setClaims, id)}
                  />
                  {CLAIMS[id]}
                </label>
                {claims.includes(id) && (
                  <label className="check small muted">
                    <input
                      type="checkbox"
                      checked={required.includes(id)}
                      onChange={() => toggle(required, setRequired, id)}
                    />
                    required
                  </label>
                )}
              </div>
            ))}
          </div>

          {claims.includes('name_match') && (
            <label className="field">
              Name you expect
              <input
                value={expectedName}
                onChange={(e) => setExpectedName(e.target.value)}
                placeholder="Chinedu Okafor"
              />
            </label>
          )}

          {error && <p className="error">{error}</p>}
          <button className="btn" disabled={busy || !lagId || !purpose || claims.length === 0}>
            {busy ? 'Sending…' : 'Send request'}
          </button>
          <p className="muted small">
            Ask only for what you need. Residents can switch off anything
            that isn’t required.
          </p>
        </form>

        <div className="card">
          <h2 style={{ marginBottom: 12 }}>Answer</h2>
          {requestId
            ? <RequestStatus id={requestId} fetchStatus={fetchStatus} />
            : <p className="muted">Send a request to see the answer here.</p>}
        </div>
      </div>
    </>
  );
}
