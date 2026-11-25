# LagID Gate (Verifier app)

The estate gate's app for LagID Connect. It scans the wave-locked
pass in a resident's LagID Wallet, checks it with the Connect API,
runs a live face check, and shows ACCESS GRANTED or DENIED. The
same result appears on the Gate Monitor (`/gate`).

Built from two existing pieces:
- the FaceMatch engine (ML Kit liveness + MobileFaceNet)
- the WaveLockedQR scanner (stitches frames, needs a moving wave)

## Set up

Create `verifier-android/local.properties` (never committed):

```
lagid.apiUrl=http://192.168.x.x:4000
lagid.gateKey=lc_test_...
```

`lagid.gateKey` is the estate's API key. Get one with
`npm run seed` (or reuse the key the Gate Monitor uses).

## How a check works

1. The resident opens **Show my pass** in the wallet and holds it
   up to the gate phone. The scanner watches the wave for about
   2 seconds; a photo or screenshot fails here.
2. The pass code goes to `/v1/gate/verify`. It works once and only
   within 30 seconds.
3. The front camera runs the live face check (blink, turn, look
   straight) and compares the face with the template the resident
   enrolled on their own phone.
4. The result goes to `/v1/gate/results` and the Gate Monitor.

Set the gate phone up facing visitors, like a kiosk: the front
camera reads the pass and then the face. Use "Switch camera" under
the scanner if you'd rather scan with the back camera.
