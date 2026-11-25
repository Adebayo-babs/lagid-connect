# LagID Wallet (Android)

The resident's app for LagID Connect: link your LagID, approve
or decline verification requests, see every check, and remove
a business's access.

Kotlin, Jetpack Compose, OkHttp, kotlinx.serialization.
The device key is a P-256 key in the Android Keystore; it signs
every API call and every approval.

## Run it

1. Open this `wallet-android` folder in Android Studio and let
   Gradle sync.
2. Start the API on your laptop (`cd api && npm run dev`).
3. Run the app:
   - **Emulator:** works as is (it reaches your laptop at
     `http://10.0.2.2:4000`).
   - **Physical phone on the same Wi-Fi:** add your laptop's
     IP to `wallet-android/local.properties`:
     ```
     lagid.apiUrl=http://192.168.x.x:4000
     ```
     then sync and run again.

## Try it

1. Link with a sample LagID and the phone number on its
   record (run `npm run probe` in `api` to see them; in mock
   mode use `LAG1000000001` and `08031234501`). The code
   shows on screen (no SMS in the sandbox).
2. Face check is a placeholder that passes for now.
3. Send a request from the API side (see the main README) and
   it appears on the home screen within 4 seconds.
