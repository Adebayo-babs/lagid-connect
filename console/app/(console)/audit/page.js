import { apiFetch, getKey } from '@/lib/api';
import { EVENT_LABELS, statusPill, when } from '@/lib/format';

export const dynamic = 'force-dynamic';

export default async function AuditPage() {
  const key = await getKey();
  const { data } = await apiFetch('/v1/events?limit=200', { key });
  const events = data.events || [];

  return (
    <>
      <div className="page-head">
        <div className="eyebrow">Audit log</div>
        <h1>Every check your business made</h1>
        <p>Residents see the same events in their wallet’s activity log.</p>
      </div>
      <div className="card">
        {events.length === 0 && <p className="muted">Nothing yet.</p>}
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Event</th>
              <th>Result</th>
              <th>Detail</th>
              <th>By</th>
            </tr>
          </thead>
          <tbody>
            {events.map((e) => (
              <tr key={e.id}>
                <td className="muted">{when(e.at)}</td>
                <td>{EVENT_LABELS[e.type] || e.type}</td>
                <td>{e.result && <span className={statusPill(e.result)}>{e.result}</span>}</td>
                <td className="muted small">{e.reason || ''}</td>
                <td className="muted small">{e.actor}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
