// Signed webhooks to a business.
// Header X-LC-Signature: t=<unix>,v1=<hex HMAC-SHA256>
// over "<t>.<raw body>" with the business's webhookSecret.

import crypto from 'node:crypto';

export function signWebhook(secret, body, t = Math.floor(Date.now() / 1000)) {
  const v1 = crypto
    .createHmac('sha256', secret)
    .update(`${t}.${body}`)
    .digest('hex');
  return `t=${t},v1=${v1}`;
}

// Fire and forget: a slow or broken webhook endpoint
// must not hold up the resident's approval.
export function sendWebhook(business, type, data) {
  if (!business?.webhookUrl || !business?.webhookSecret) return;

  const body = JSON.stringify({
    type,
    created: new Date().toISOString(),
    data,
  });

  fetch(business.webhookUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-LC-Signature': signWebhook(business.webhookSecret, body),
    },
    body,
    signal: AbortSignal.timeout(5000),
  })
    .then((res) => {
      if (!res.ok) {
        console.warn(`[webhook] ${type} -> ${res.status}`);
      }
    })
    .catch((err) => {
      console.warn(`[webhook] ${type} failed: ${err.message}`);
    });
}
