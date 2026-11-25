import { config } from './config.js';
import { connectDb } from './db.js';
import { loadSigningKey } from './lib/keys.js';
import { createApp } from './app.js';

async function main() {
  await loadSigningKey();

  try {
    await connectDb();
  } catch (err) {
    console.error('[db] connection failed:', err.message);
  }

  const app = createApp();
  const server = app.listen(config.port, () => {
    const mode = config.sandbox.mock ? 'MOCK' : 'LIVE';
    console.log(
      `[api] listening on ${config.publicUrl} ` +
        `(sandbox: ${mode})`
    );
  });
  // Node closes idle connections after 5s by default, which
  // can reset a client that reuses one. Keep them longer.
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 66_000;
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
