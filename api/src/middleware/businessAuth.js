// Business API auth: Authorization: Bearer lc_test_...
// Keys are stored only as SHA-256 hashes.

import { ApiKey, Business } from '../models/index.js';
import { sha256 } from '../lib/crypto.js';
import { HttpError, wrap } from './errors.js';

export const businessAuth = wrap(async (req, res, next) => {
  const header = req.get('authorization') || '';
  const match = header.match(/^Bearer\s+(lc_(?:test|live)_\S+)$/);
  if (!match) {
    throw new HttpError(
      401,
      'missing_api_key',
      'Send your API key as: Authorization: Bearer lc_test_...'
    );
  }

  const apiKey = await ApiKey.findOne({
    keyHash: sha256(match[1]),
    revokedAt: null,
  });
  if (!apiKey) {
    throw new HttpError(401, 'invalid_api_key', 'API key not valid');
  }

  const business = await Business.findById(apiKey.businessId);
  if (!business) {
    throw new HttpError(401, 'invalid_api_key', 'API key not valid');
  }

  // Don't wait on this write
  ApiKey.updateOne(
    { _id: apiKey._id },
    { lastUsedAt: new Date() }
  ).catch(() => {});

  req.business = business;
  req.apiKey = apiKey;
  next();
});

// Audience string used in tokens issued to a business
export const businessAudience = (business) => `biz_${business._id}`;
