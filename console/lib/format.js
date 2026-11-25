// Small display helpers shared by server and client pages

export const CLAIMS = {
  lagos_resident: 'Verified Lagos resident',
  name_match: 'Name matches',
  full_name: 'Full name',
  age_over_18: 'Over 18',
  lga: 'Local government area',
  photo: 'Photo',
  phone_verified: 'Phone verified',
};

export const claimLabel = (id) => CLAIMS[id] || id;

export function when(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('en-GB', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function statusPill(status) {
  const kind = {
    approved: 'good',
    granted: 'good',
    done: 'good',
    declined: 'bad',
    denied: 'bad',
    expired: 'bad',
    revoked: 'bad',
    pending: 'warn',
  }[status] || '';
  return `pill ${kind}`;
}

export const EVENT_LABELS = {
  'request.created': 'Request sent',
  'request.approved': 'Resident approved',
  'request.declined': 'Resident declined',
  'request.expired': 'Request expired',
  'grant.revoked': 'Resident removed access',
  'gate.result': 'Gate check',
  'batch.verified': 'Batch verification',
  'wallet.linked': 'Wallet linked',
};
