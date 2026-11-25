# LagID Connect: Business Console

Next.js app for businesses using LagID Connect:

- **Dashboard**: requests, gate entries and batch checks
- **Verify a resident**: send a consent request, watch the signed
  answer arrive, signature checked with the public keys
- **Batch verification**: up to 100 LagIDs from a CSV, using the
  LagID registry's batch lookup; yes or no answers only
- **Audit log** and **Developers** (curl and token-check snippets)
- **Showcase**: `/showcase/ikeja-heights`, an estate's tenancy
  page with a "Verify with LagID" button

The business API key is kept in an httpOnly cookie and only used
on the server. Browser code never sees it.

## Run

```bash
cd console
npm install
cp .env.example .env.local
npm run dev
```

Open http://localhost:3000 and sign in with a key from
`npm run key` (in the `api` folder). The API must be running.
