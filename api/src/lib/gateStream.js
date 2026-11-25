// Live gate results, pushed to Gate Monitor pages with
// Server-Sent Events. One set of open pages per business.

const pages = new Map(); // businessId -> Set<res>

export function addGatePage(businessId, res) {
  const key = String(businessId);
  if (!pages.has(key)) pages.set(key, new Set());
  pages.get(key).add(res);
  return () => pages.get(key)?.delete(res);
}

export function broadcastGate(businessId, data) {
  const set = pages.get(String(businessId));
  if (!set) return 0;
  const line = `event: gate\ndata: ${JSON.stringify(data)}\n\n`;
  for (const res of set) res.write(line);
  return set.size;
}
