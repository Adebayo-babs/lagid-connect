// Turns a LagID sandbox profile into the one shape the
// rest of the app uses.
//
// Sandbox envelope: { status, message, data }
// Profile fields (snake_case): lag_id, first_name,
// middle_name, last_name, date_of_birth, gender,
// marital_status, phone_number, email, address,
// state_of_origin, nationality, occupation,
// place_of_birth, image_url, date_of_registration,
// verification_status
//
// A record the sandbox returns is active (inactive ones
// give 404). verification_status says whether it is a
// verified sample.

function pick(obj, keys) {
  for (const key of keys) {
    const value = obj?.[key];
    if (value !== undefined && value !== null && value !== '') {
      return value;
    }
  }
  return undefined;
}

// { status, message, data: {...} } -> {...}
export function unwrap(body) {
  if (!body || typeof body !== 'object') return body;
  if ('data' in body && body.data && typeof body.data === 'object') {
    return body.data;
  }
  return body;
}

export function normalizeResident(raw) {
  const r = unwrap(raw);
  if (!r || typeof r !== 'object') return null;

  const firstName = pick(r, ['first_name', 'firstName']) || '';
  const middleName = pick(r, ['middle_name', 'middleName']) || '';
  const lastName = pick(r, ['last_name', 'lastName']) || '';
  const fullName = [firstName, middleName, lastName]
    .filter(Boolean)
    .join(' ');

  const verified = pick(r, ['verification_status', 'verified']);

  return {
    lagId: pick(r, ['lag_id', 'lagId']),
    firstName,
    middleName,
    lastName,
    fullName,
    dateOfBirth: pick(r, ['date_of_birth', 'dateOfBirth']),
    gender: pick(r, ['gender']),
    phone: pick(r, ['phone_number', 'phone']),
    email: pick(r, ['email']),
    address: pick(r, ['address']),
    lga: pick(r, ['lga', 'local_government_area']),
    stateOfOrigin: pick(r, ['state_of_origin']),
    nationality: pick(r, ['nationality']),
    photoUrl: pick(r, ['image_url', 'photo_url']),
    registeredOn: pick(r, ['date_of_registration']),
    // Returned by the sandbox = active record
    status: pick(r, ['status']) === 'inactive' ? 'inactive' : 'active',
    verified: verified === undefined ? true : verified === true,
  };
}
