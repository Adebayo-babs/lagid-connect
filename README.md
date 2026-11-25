# LagID Connect

"Verify with LagID": a consent-based identity layer for Lagos.
A business asks for specific facts about a resident, the
resident approves on their phone, and the business receives a
signed answer instead of a copy of their ID.

Built for the Identiko LagID Hackathon 2026.

## Repo layout

| Folder | What |
| --- | --- |
| `api/` | Connect API: Node.js, Express, MongoDB |
| `console/` | Business Console and showcase apps (Next.js), coming |
| `wallet-android/` | Resident Wallet (Kotlin, Compose), coming |
| `verifier-android/` | Verifier app (Kotlin, Compose), coming |

## How LagID is used

Every resident fact comes from the LagID Sandbox API, called
only from the Connect API (the key never reaches a phone):

- `GET /api/v1/identity/lag/{lagId}` when a resident links
  their wallet and on every approved request
- `POST /api/v1/identity/lag/batch` for batch verification
- `GET /api/v1/identity/verify` to list demo residents

## Run the API

```bash
cd api
npm install
cp .env.example .env
npm run keys        # paste the two lines into .env
npm run dev
```

Open http://localhost:4000/health.

With `SANDBOX_MOCK=true` the API uses five built-in mock
residents, so it works before you have a sandbox key.

### Switch to the real sandbox

1. Put your team key in `SANDBOX_API_KEY`
2. Check `SANDBOX_BASE_URL` against the portal docs
3. Set `SANDBOX_MOCK=false`
4. Run `npm run probe` and open `api/samples/` to see the real
   field names, then adjust `src/lib/normalize.js` if needed

### Dev routes (development only)

| Route | What it does |
| --- | --- |
| `GET /v1/dev/residents` | List sample residents |
| `GET /v1/dev/lookup/:lagId` | Look up one resident |
| `POST /v1/dev/batch` | `{ "lagIds": [...] }` |
| `GET /v1/dev/claim-types` | Claims a business can ask for |
| `POST /v1/dev/claims-preview` | `{ lagId, expectedName, claims }` returns derived claims and a signed token |
| `GET /.well-known/jwks.json` | Public signing keys |

Mock LagIDs: `LAG1000000001` (Tunde Bakare) to
`LAG1000000005` (inactive record).

### Check the consent flow

With the API running, in a second terminal:

```bash
npm run seed   # once
npm run demo
```

`demo` plays both a resident's phone and a business, and
prints a pass or fail line for each step.

## Business API

Auth: `Authorization: Bearer lc_test_...`

| Method | Route | What it does |
| --- | --- | --- |
| POST | `/v1/requests` | Ask a resident for claims: `{ lagId, claims, requiredClaims?, purpose, expectedName?, reference?, ttlMinutes? }` |
| GET | `/v1/requests` | Recent requests |
| GET | `/v1/requests/:id` | Status, and the signed claims token once approved |
| GET | `/v1/events` | Audit log |

Webhooks (if the business has a `webhookUrl`):
`request.approved`, `request.declined`, `request.expired`,
`grant.revoked`, signed in `X-LC-Signature` as
`t=<unix>,v1=<HMAC-SHA256 of "t.body">`.

## Wallet API

Linking is open. Everything else needs a device token: a JWT
the phone signs with its own P-256 key, header `kid` = the
device id, `aud` = `lagid-connect-wallet`, valid up to 5 min.

| Method | Route | What it does |
| --- | --- | --- |
| POST | `/v1/wallet/link/start` | `{ lagId, phone }` sends a one-time code |
| POST | `/v1/wallet/link/complete` | `{ linkId, code, publicKey, faceMatch }` |
| GET | `/v1/wallet/me` | The linked resident |
| GET | `/v1/wallet/requests` | Requests, `?status=pending` |
| POST | `/v1/wallet/requests/:id/decision` | Approve or decline, signed by the device |
| GET | `/v1/wallet/activity` | Every check, newest first |
| GET | `/v1/wallet/grants` | Businesses with access |
| DELETE | `/v1/wallet/grants/:id` | Revoke |

A decision carries `signature`, a compact JWS from the device
over `{ rid, decision, claims (sorted), iat }`. Approvals also
send `lagId`, which the server checks against its stored hash
before a fresh sandbox lookup.
