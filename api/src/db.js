import dns from 'node:dns';
import mongoose from 'mongoose';
import { config } from './config.js';

let connected = false;

export async function connectDb() {
  if (!config.mongoUri) {
    console.warn(
      '[db] MONGODB_URI not set. Running without a database.'
    );
    return false;
  }
  // Some ISP DNS servers refuse the SRV lookup that
  // mongodb+srv:// needs (querySrv ESERVFAIL).
  // DNS_SERVERS lets Node use public resolvers instead.
  const servers = (process.env.DNS_SERVERS || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  if (servers.length) dns.setServers(servers);

  mongoose.set('strictQuery', true);
  await mongoose.connect(config.mongoUri);
  connected = true;
  console.log('[db] connected');
  return true;
}

export function dbReady() {
  return connected && mongoose.connection.readyState === 1;
}
