// Gate Monitor: shows each gate result full screen,
// with a beep for GRANTED and an alarm for DENIED.

const $ = (id) => document.getElementById(id);
let audio;
let resetTimer;

function store(key, value) {
  try {
    if (value === undefined) return localStorage.getItem(key);
    localStorage.setItem(key, value);
  } catch {
    return null;
  }
  return null;
}

// Key from ?key=..., or the last one used here
const params = new URLSearchParams(location.search);
$('key').value = params.get('key') || store('lc_gate_key') || '';

$('go').addEventListener('click', () => {
  const key = $('key').value.trim();
  if (!key) return;
  store('lc_gate_key', key);
  audio = new (window.AudioContext || window.webkitAudioContext)();
  $('start').classList.add('hidden');
  connect(key);
});

function connect(key) {
  const source = new EventSource(
    `/v1/gate/stream?key=${encodeURIComponent(key)}`
  );
  source.addEventListener('hello', (e) => {
    const { business } = JSON.parse(e.data);
    $('business').textContent = `${business} · Gate Monitor`;
    $('status').textContent = 'Live';
  });
  source.addEventListener('gate', (e) => show(JSON.parse(e.data)));
  source.onerror = () => {
    $('status').textContent = 'Reconnecting…';
  };
}

function initials(name) {
  return String(name || '?')
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

function show(r) {
  const card = $('card');
  card.className = `card ${r.granted ? 'granted' : 'denied'}`;
  $('verdict').textContent = r.granted ? 'ACCESS GRANTED' : 'ACCESS DENIED';

  if (r.name) {
    $('avatar').textContent = initials(r.name);
    $('avatar').classList.remove('hidden');
    $('name').textContent = r.name;
  } else {
    $('avatar').classList.add('hidden');
    $('name').textContent = 'Unknown pass';
  }

  const time = new Date(r.at).toLocaleTimeString();
  $('meta').textContent = r.lagIdLast4
    ? `LagID •••• ${r.lagIdLast4} · ${time}`
    : time;
  $('reason').textContent = r.reason || '';

  sound(r.granted);
  clearTimeout(resetTimer);
  resetTimer = setTimeout(idle, 8000);
}

function idle() {
  $('card').className = 'card idle';
  $('verdict').textContent = 'READY';
  $('avatar').classList.add('hidden');
  $('name').textContent = 'Waiting for the next resident';
  $('meta').textContent = '';
  $('reason').textContent = '';
}

function tone(freq, start, length) {
  const osc = audio.createOscillator();
  const gain = audio.createGain();
  osc.frequency.value = freq;
  osc.type = 'sine';
  gain.gain.setValueAtTime(0.25, audio.currentTime + start);
  gain.gain.exponentialRampToValueAtTime(
    0.001, audio.currentTime + start + length
  );
  osc.connect(gain).connect(audio.destination);
  osc.start(audio.currentTime + start);
  osc.stop(audio.currentTime + start + length);
}

function sound(granted) {
  if (!audio) return;
  if (granted) {
    tone(880, 0, 0.18);
    tone(1320, 0.2, 0.25);
  } else {
    for (let i = 0; i < 3; i += 1) tone(330, i * 0.3, 0.25);
  }
}
