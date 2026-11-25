import 'dotenv/config';

function bool(value, fallback = false) {
  if (value === undefined || value === '') return fallback;
  return ['1', 'true', 'yes'].includes(
    String(value).toLowerCase()
  );
}

const port = Number(process.env.PORT || 4000);

export const config = {
  port,
  env: process.env.NODE_ENV || 'development',
  publicUrl:
    process.env.PUBLIC_URL || `http://localhost:${port}`,

  mongoUri: process.env.MONGODB_URI || '',

  sandbox: {
    baseUrl: (process.env.SANDBOX_BASE_URL || '')
      .replace(/\/+$/, ''),
    apiKey: process.env.SANDBOX_API_KEY || '',
    mock: bool(process.env.SANDBOX_MOCK, true),
    cacheTtl: Number(process.env.SANDBOX_CACHE_TTL || 60),
  },

  hashSalt: process.env.LAGID_HASH_SALT || 'dev-salt',

  // The sandbox can't send SMS, so in demo mode the one-time
  // code is returned to the app and shown on screen.
  demoOtp: bool(process.env.DEMO_OTP, false),

  signing: {
    kid: process.env.SIGNING_KID || '',
    privateJwk: process.env.SIGNING_PRIVATE_JWK || '',
  },
};

export const isDev = config.env !== 'production';
