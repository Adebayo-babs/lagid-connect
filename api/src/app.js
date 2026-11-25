import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';

import { isDev } from './config.js';
import { healthRouter } from './routes/health.js';
import { jwksRouter } from './routes/jwks.js';
import { devRouter } from './routes/dev.js';
import { walletRouter } from './routes/wallet.js';
import { businessRouter } from './routes/business.js';
import { gateRouter } from './routes/gate.js';

import { notFound, errorHandler } from './middleware/errors.js';

const publicDir = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  'public'
);

export function createApp() {
  const app = express();

  app.disable('x-powered-by');
  app.use(
    helmet({
      // The API also runs over plain http on a LAN during
      // development, so don't force https sub-requests.
      contentSecurityPolicy: {
        directives: { upgradeInsecureRequests: null },
      },
    })
  );
  app.use(cors());
  app.use(express.json({ limit: '2mb' }));
  app.use(morgan(isDev ? 'dev' : 'combined'));

  app.get('/', (req, res) => {
    res.json({
      service: 'LagID Connect API',
      health: '/health',
      jwks: '/.well-known/jwks.json',
    });
  });

  // Gate Monitor page and its script
  app.get('/gate', (req, res) => {
    res.sendFile(path.join(publicDir, 'gate.html'));
  });
  app.use(express.static(publicDir, { index: false }));

  app.use(healthRouter);
  app.use(jwksRouter);

  // Dev-only helpers for poking the sandbox
  if (isDev) app.use('/v1/dev', devRouter);

  // Order matters: the business router checks an API key
  // on every path it sees, so it goes last.
  app.use('/v1/wallet', walletRouter);
  app.use('/v1/gate', gateRouter);
  app.use('/v1', businessRouter);

  app.use(notFound);
  app.use(errorHandler);
  return app;
}
