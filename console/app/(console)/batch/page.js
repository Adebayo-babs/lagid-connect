'use client';

import { useState } from 'react';

const SAMPLE = `lag_id,expected_name
LAG100001,Adeola Johnson
LAG100002,Chinedu Okafor
LAG100003,Wrong Name
LAG999999,Someone Else
not-an-id,Typo`;

// "lag_id,expected_name" per line; header row optional
function parseCsv(text) {
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  if (lines.length && /^lag[_ ]?id\b/i.test(lines[0])) lines.shift();
  return lines.map((line) => {
    const [lagId, ...rest] = line.split(',');
    const name = rest.join(',').trim().replace(/^"|"$/g, '');
    return { lagId: (lagId || '').trim(), expectedName: name || undefined };
  });
}

function yesNo(v) {
  if (v === true) return <span className="pill good">Yes</span>;
  if (v === false) return <span className="pill bad">No</span>;
  return <span className="muted">—</span>;
}

export default function BatchPage() {
  const [text, setText] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const items = parseCsv(text);

  async function loadFile(e) {
    const file = e.target.files?.[0];
    if (file) setText(await file.text());
  }

  async function run() {
    setBusy(true);
    setError('');
    setResult(null);
    const res = await fetch('/api/proxy/verify/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ items }),
    });
    const data = await res.json().catch(() => ({}));
    setBusy(false);
    if (!res.ok) return setError(data.error?.message || 'Batch failed');
    setResult(data);
  }

  function exportCsv() {
    const rows = [['lag_id', 'status', 'verified', 'name_match', 'over_18']];
    for (const r of result.results) {
      rows.push([r.lagId, r.status, r.verified, r.nameMatch ?? '', r.over18 ?? '']);
    }
    const blob = new Blob([rows.map((r) => r.join(',')).join('\n')], {
      type: 'text/csv',
    });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'lagid-batch-results.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }

  return (
    <>
      <div className="page-head">
        <div className="eyebrow">Batch verification</div>
        <h1>Check up to 100 LagIDs at once</h1>
        <p>
          For onboarding staff or tenants in bulk. Uses the LagID
          registry’s batch lookup and returns only yes or no answers.
        </p>
      </div>

      <div className="card stack">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <strong>One per line: LagID, expected name</strong>
          <div className="row">
            <label className="btn ghost">
              Upload CSV
              <input type="file" accept=".csv,text/csv" onChange={loadFile} hidden />
            </label>
            <button className="btn ghost" type="button" onClick={() => setText(SAMPLE)}>
              Use sample
            </button>
          </div>
        </div>
        <textarea
          rows={8}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={SAMPLE}
        />
        <div className="row">
          <button
            className="btn"
            onClick={run}
            disabled={busy || items.length === 0 || items.length > 100}
          >
            {busy ? 'Checking…' : `Check ${items.length} LagID${items.length === 1 ? '' : 's'}`}
          </button>
          {items.length > 100 && <span className="error">At most 100 per batch.</span>}
          {error && <span className="error">{error}</span>}
        </div>
      </div>

      {result && (
        <div className="section">
          <div className="grid four">
            <Summary n={result.summary.verified} label="Verified residents" />
            <Summary n={result.summary.nameMismatch} label="Name doesn’t match" />
            <Summary n={result.summary.notFound} label="Not in the registry" />
            <Summary n={result.summary.invalid} label="Invalid LagID format" />
          </div>

          <div className="card section">
            <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
              <h2>Results</h2>
              <button className="btn ghost" onClick={exportCsv}>Export CSV</button>
            </div>
            <table>
              <thead>
                <tr>
                  <th>LagID</th>
                  <th>Registry</th>
                  <th>Verified resident</th>
                  <th>Name matches</th>
                  <th>Over 18</th>
                </tr>
              </thead>
              <tbody>
                {result.results.map((r, i) => (
                  <tr key={`${r.lagId}-${i}`}>
                    <td><code>{r.lagId}</code></td>
                    <td>
                      <span className={
                        r.status === 'FOUND' ? 'pill good'
                          : r.status === 'INVALID_ID' ? 'pill warn' : 'pill bad'
                      }>
                        {r.status.replace('_', ' ').toLowerCase()}
                      </span>
                    </td>
                    <td>{yesNo(r.verified)}</td>
                    <td>{yesNo(r.nameMatch)}</td>
                    <td>{yesNo(r.over18)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="muted small" style={{ marginTop: 12 }}>
              No names, phone numbers or addresses come back. To share
              more, a resident has to approve a request themselves.
            </p>
          </div>
        </div>
      )}
    </>
  );
}

function Summary({ n, label }) {
  return (
    <div className="card stat">
      <div className="num">{n}</div>
      <div className="label">{label}</div>
    </div>
  );
}
