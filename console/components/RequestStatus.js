'use client';

import { useEffect, useState } from 'react';
import { claimLabel } from '@/lib/format';

/**
 * Follows one consent request until the resident answers,
 * then shows the signed claims and checks the signature.
 * `fetchStatus(id)` returns { request } from the API.
 */
export default function RequestStatus({ id, fetchStatus, compact = false }) {
  const [request, setRequest] = useState(null);
  const [check, setCheck] = useState(null);

  useEffect(() => {
    let stop = false;
    let timer;
    async function poll() {
      const data = await fetchStatus(id).catch(() => null);
      if (stop) return;
      if (data?.request) setRequest(data.request);
      if (!data?.request || data.request.status === 'pending') {
        timer = setTimeout(poll, 2000);
      }
    }
    poll();
    return () => { stop = true; clearTimeout(timer); };
  }, [id, fetchStatus]);

  useEffect(() => {
    if (!request?.token) return;
    fetch('/api/check-token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: request.token }),
    })
      .then((r) => r.json())
      .then(setCheck)
      .catch(() => setCheck({ valid: false }));
  }, [request?.token]);

  if (!request) return <p className="muted">Sending…</p>;

  if (request.status === 'pending') {
    return (
      <div className="stack">
        <span className="pill warn">Waiting for the resident</span>
        <p className="muted">
          The request is on the resident’s phone. They choose what to
          share and approve with their device key.
        </p>
      </div>
    );
  }

  if (request.status !== 'approved') {
    return (
      <div className="stack">
        <span className="pill bad">{request.status}</span>
        <p className="muted">
          {request.status === 'declined'
            ? 'The resident declined. Nothing was shared.'
            : 'The resident didn’t answer in time.'}
        </p>
      </div>
    );
  }

  const claims = check?.payload?.claims || {};

  return (
    <div className="stack">
      <div className="row">
        <span className="pill good">Approved</span>
        {check && (
          <span className={check.valid ? 'pill good' : 'pill bad'}>
            {check.valid ? 'Signature verified (ES256)' : 'Signature NOT valid'}
          </span>
        )}
      </div>
      <table>
        <tbody>
          {Object.entries(claims).map(([k, v]) => (
            <tr key={k}>
              <td>{claimLabel(k)}</td>
              <td style={{ fontWeight: 600 }}>
                {v === true ? 'Yes' : v === false ? 'No' : String(v ?? '—')}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!compact && check?.payload && (
        <details>
          <summary className="muted small">Signed token payload</summary>
          <pre className="code" style={{ marginTop: 8 }}>
            {JSON.stringify(check.payload, null, 2)}
          </pre>
        </details>
      )}
      <p className="muted small">
        No LagID, phone or address in the answer. Only what the
        resident chose to share.
      </p>
    </div>
  );
}
