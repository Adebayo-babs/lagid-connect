import { Router } from 'express';
import { config } from '../config.js';
import { dbReady } from '../db.js';

export const healthRouter = Router();

healthRouter.get('/health', (req, res) => {
  res.json({
    ok: true,
    service: 'lagid-connect-api',
    db: dbReady() ? 'connected' : 'not connected',
    sandbox: config.sandbox.mock ? 'mock' : 'live',
    time: new Date().toISOString(),
  });
});
