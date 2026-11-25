import Link from 'next/link';
import { apiFetch, getKey } from '@/lib/api';
import { EVENT_LABELS, claimLabel, statusPill, when } from '@/lib/format';

export const dynamic = 'force-dynamic';

export default async function Dashboard() {
  const key = await getKey();
  const [stats, requests, events] = await Promise.all([
    apiFetch('/v1/stats', { key }),
    apiFetch('/v1/requests?limit=6', { key }),
    apiFetch('/v1/events?limit=8', { key }),
  ]);

  const s = stats.data || {};
  const r = s.requests || {};
  const g = s.gate || {};

  return (
    <>
      <div className="page-head">
        <div className="eyebrow">Dashboard</div>
        <h1>Verifications at a glance</h1>
        <p>Every answer here was checked against the LagID registry.</p>
      </div>

      <div className="grid four">
        <Stat num={r.approved ?? 0} label="Requests approved" />
        <Stat num={r.pending ?? 0} label="Waiting for residents" />
        <Stat num={g.granted ?? 0} label="Gate entries granted" />
        <Stat num={s.batchChecks ?? 0} label="LagIDs batch-checked" />
      </div>

      <div className="grid two section">
        <div className="card">
          <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
            <h2>Recent requests</h2>
            <Link className="btn ghost" href="/verify">New request</Link>
          </div>
          {(requests.data.requests || []).length === 0 && (
            <p className="muted">No requests yet.</p>
          )}
          <table>
            <tbody>
              {(requests.data.requests || []).map((q) => (
                <tr key={q.id}>
                  <td>
                    <div style={{ fontWeight: 600 }}>{q.purpose}</div>
                    <div className="muted small">
                      {q.claims.map(claimLabel).join(' · ')}
                    </div>
                  </td>
                  <td><span className={statusPill(q.status)}>{q.status}</span></td>
                  <td className="muted small">{when(q.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="card">
          <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
            <h2>Audit log</h2>
            <Link className="btn ghost" href="/audit">See all</Link>
          </div>
          <table>
            <tbody>
              {(events.data.events || []).map((e) => (
                <tr key={e.id}>
                  <td>{EVENT_LABELS[e.type] || e.type}</td>
                  <td>
                    {e.result && <span className={statusPill(e.result)}>{e.result}</span>}
                  </td>
                  <td className="muted small">{when(e.at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}

function Stat({ num, label }) {
  return (
    <div className="card stat">
      <div className="num">{num}</div>
      <div className="label">{label}</div>
    </div>
  );
}
