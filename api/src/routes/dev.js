// Development-only routes for checking the sandbox
// and the signing setup. Not mounted in production.

import { Router } from 'express';
import { wrap, HttpError } from '../middleware/errors.js';
import {
  lookupResident,
  lookupBatch,
  listSampleResidents,
} from '../lib/sandbox.js';
import { deriveClaims, CLAIM_TYPES } from '../lib/claims.js';
import { signToken, verifyToken } from '../lib/keys.js';
import { randomId } from '../lib/crypto.js';

export const devRouter = Router();

// Strip the photo so responses stay readable
const slim = (r) => r;

devRouter.get('/residents', wrap(async (req, res) => {
  const list = await listSampleResidents();
  res.json({ count: list.length, residents: list.map(slim) });
}));

devRouter.get('/lookup/:lagId', wrap(async (req, res) => {
  const resident = await lookupResident(req.params.lagId);
  if (!resident) {
    throw new HttpError(404, 'resident_not_found', 'No such LagID');
  }
  res.json({ resident: slim(resident) });
}));

devRouter.post('/batch', wrap(async (req, res) => {
  const result = await lookupBatch(req.body?.lagIds);
  res.json({
    found: result.found.map(slim),
    missing: result.missing,
    invalid: result.invalid,
  });
}));

devRouter.get('/claim-types', (req, res) => {
  res.json(CLAIM_TYPES);
});

// Try the whole claims pipeline without a database:
// POST { lagId, claims: [...], expectedName }
devRouter.post('/claims-preview', wrap(async (req, res) => {
  const { lagId, claims, expectedName } = req.body || {};
  const resident = await lookupResident(lagId);
  if (!resident) {
    throw new HttpError(404, 'resident_not_found', 'No such LagID');
  }

  const wanted = claims?.length
    ? claims
    : ['lagos_resident', 'name_match', 'age_over_18'];
  const derived = deriveClaims(resident, wanted, { expectedName });

  const token = await signToken(
    { purpose: 'Dev preview', claims: derived },
    {
      audience: 'biz_dev',
      subject: 'res_dev',
      ttl: '1h',
      jti: randomId('req'),
    }
  );
  const verified = await verifyToken(token, { audience: 'biz_dev' });

  res.json({ claims: derived, token, verified });
}));
