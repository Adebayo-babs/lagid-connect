import { getKey } from './api';

// The showcase estate's key: SHOWCASE_API_KEY, or the key
// you're signed in to the console with.
export async function showcaseKey() {
  return process.env.SHOWCASE_API_KEY || (await getKey());
}
