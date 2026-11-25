import { Router } from 'express';
import { jwks } from '../lib/keys.js';

export const jwksRouter = Router();

// Public keys anyone can use to check our tokens
jwksRouter.get('/.well-known/jwks.json', (req, res) => {
  res.set('Cache-Control', 'public, max-age=300');
  res.json(jwks());
});
