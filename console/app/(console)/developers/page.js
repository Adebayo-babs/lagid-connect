import { API_URL, apiFetch, getKey } from '@/lib/api';

export const dynamic = 'force-dynamic';

export default async function DevelopersPage() {
  const key = await getKey();
  const { data: me } = await apiFetch('/v1/me', { key });
  const base = process.env.PUBLIC_API_URL || API_URL;

  const create = `curl -X POST ${base}/v1/requests \\
  -H "Authorization: Bearer ${me.keyPrefix}..." \\
  -H "Content-Type: application/json" \\
  -d '{
    "lagId": "LAG100002",
    "purpose": "Tenancy application, Flat 4B",
    "claims": ["lagos_resident", "name_match", "age_over_18"],
    "requiredClaims": ["lagos_resident", "name_match"],
    "expectedName": "Chinedu Okafor"
  }'`;

  const read = `curl ${base}/v1/requests/REQUEST_ID \\
  -H "Authorization: Bearer ${me.keyPrefix}..."`;

  const verify = `import { createRemoteJWKSet, jwtVerify } from 'jose';

const jwks = createRemoteJWKSet(
  new URL('${base}/.well-known/jwks.json')
);
const { payload } = await jwtVerify(token, jwks, {
  audience: 'biz_${me.id}',
});
// payload.claims = { lagos_resident: true, ... }`;

  return (
    <>
      <div className="page-head">
        <div className="eyebrow">Developers</div>
        <h1>Add “Verify with LagID” to your app</h1>
        <p>Three calls: ask, wait for the resident, check the signature.</p>
      </div>

      <div className="grid two">
        <div className="card stack">
          <h2>Your key</h2>
          <p>
            <code>{me.keyPrefix}…</code>{' '}
            <span className="muted small">(full key shown only once, when made)</span>
          </p>
          <p className="muted small">
            Keep it on your server. Never put it in a browser or a phone app.
          </p>
          <h2>Audience</h2>
          <p><code>biz_{me.id}</code></p>
          <p className="muted small">Tokens for your business carry this <code>aud</code>.</p>
        </div>
        <div className="card stack">
          <h2>Public keys</h2>
          <p><code>{base}/.well-known/jwks.json</code></p>
          <p className="muted small">
            Anyone can check a LagID Connect token with these keys. No
            call back to us is needed.
          </p>
          <h2>Webhooks</h2>
          <p className="muted small">
            <code>request.approved</code>, <code>request.declined</code>,{' '}
            <code>request.expired</code>, <code>grant.revoked</code>, signed
            in <code>X-LC-Signature</code>.
          </p>
        </div>
      </div>

      <div className="section stack">
        <h2>1. Ask the resident</h2>
        <pre className="code">{create}</pre>
        <h2>2. Read the answer (or wait for the webhook)</h2>
        <pre className="code">{read}</pre>
        <h2>3. Check the signature</h2>
        <pre className="code">{verify}</pre>
      </div>
    </>
  );
}
