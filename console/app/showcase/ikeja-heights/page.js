'use client';

import { useCallback, useState } from 'react';
import RequestStatus from '@/components/RequestStatus';
import styles from './page.module.css';

// Showcase: a landlord's website using "Verify with LagID"
// instead of asking for a photocopy of an ID card.
export default function IkejaHeights() {
  const [fullName, setFullName] = useState('');
  const [lagId, setLagId] = useState('');
  const [flat, setFlat] = useState('Flat 4B');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [requestId, setRequestId] = useState(null);

  const fetchStatus = useCallback(
    (id) => fetch(`/api/showcase/request/${id}`).then((r) => r.json()),
    []
  );

  async function verify(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    const res = await fetch('/api/showcase/request', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fullName, lagId, flat }),
    });
    const data = await res.json().catch(() => ({}));
    setBusy(false);
    if (!res.ok) {
      return setError(
        data.error?.code === 'resident_not_linked'
          ? 'This LagID hasn’t set up the LagID Wallet yet.'
          : data.error?.message || 'Couldn’t start verification'
      );
    }
    setRequestId(data.request.id);
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.logo}>Ikeja Heights <span>Estate</span></div>
        <div className={styles.nav}>Homes · Amenities · Apply</div>
      </header>

      <section className={styles.hero}>
        <div>
          <p className={styles.kicker}>Now letting</p>
          <h1>Apply for a home at Ikeja Heights</h1>
          <p className={styles.lead}>
            No photocopies of your ID. Verify with LagID in seconds and
            share only what we need.
          </p>
        </div>
      </section>

      <main className={styles.body}>
        <form className={styles.form} onSubmit={verify}>
          <h2>Tenancy application</h2>
          <label>
            Full name
            <input value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Chinedu Okafor" />
          </label>
          <label>
            Your LagID
            <input value={lagId} onChange={(e) => setLagId(e.target.value)} placeholder="LAG100002" />
          </label>
          <label>
            Home
            <select value={flat} onChange={(e) => setFlat(e.target.value)}>
              <option>Flat 4B</option>
              <option>Flat 2A</option>
              <option>Terrace 7</option>
            </select>
          </label>

          {!requestId && (
            <button
              className={styles.lagid}
              disabled={busy || !fullName || !lagId}
            >
              <span className={styles.mark} aria-hidden>✓</span>
              {busy ? 'Contacting LagID…' : 'Verify with LagID'}
            </button>
          )}
          {error && <p className={styles.error}>{error}</p>}

          <p className={styles.note}>
            We’ll ask for: verified Lagos resident, name matches, over 18.
            You approve it in your LagID Wallet.
          </p>
        </form>

        <aside className={styles.status}>
          <h2>Verification</h2>
          {requestId ? (
            <RequestStatus id={requestId} fetchStatus={fetchStatus} compact />
          ) : (
            <p className={styles.muted}>
              Press “Verify with LagID”. A request appears in your LagID
              Wallet, and the answer shows here.
            </p>
          )}
        </aside>
      </main>

      <footer className={styles.footer}>
        Ikeja Heights Estate is a demo business for the LagID Hackathon.
        Verification by LagID Connect.
      </footer>
    </div>
  );
}
