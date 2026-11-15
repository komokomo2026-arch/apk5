# Ekambani Main – Android app (GitHub Actions build)

Packages the existing website `https://komokomo2026-arch.github.io/pos12/` as an Android app.
The website is **not** modified. App name: **Ekambani Main** · APK: `Ekambani-Main.apk` · AAB: `Ekambani-Main.aab`.

## Build the APK (GitHub Actions)
1. Upload this whole project to a GitHub repository (branch `main`).
2. Create `.github/workflows/` on GitHub and upload the separate file `android-build.yml` into it (it is intentionally not inside this ZIP).
3. Open the **Actions** tab → **Android Build - Ekambani Main APK** → **Run workflow** → choose `apk_and_aab` → **Run workflow**.
   (A push to `main` also builds automatically.)
4. When the run is green, open it and download **Ekambani-Main-APK** (and **Ekambani-Main-AAB**) from **Artifacts**.
   GitHub delivers artifacts as a .zip – unzip it to get `Ekambani-Main.apk`.
5. The workflow fails (red) – instead of pretending success – if no valid, signed APK with the label "Ekambani Main" is produced.

## Install on the phone
Copy `Ekambani-Main.apk` to the phone → open it → allow "Install unknown apps" for your file manager/browser → Install.
Open the app **once with internet** so the website's service worker can cache itself; after that it works offline.

## Updating without losing data
Every build gets a higher version number automatically and is signed with the same key, so installing a new APK
**updates** the app and keeps all its data (IndexedDB, localStorage, cookies). Always keep using the same key:
`my-upload-key.jks` (alias `upload`) is in the repo. For better security put your own key in GitHub
(Settings → Secrets → Actions): `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, optional `KEY_ALIAS`.
Never switch keys after users have installed the app – Android would refuse the update.
**Back up the key file somewhere safe.**

## What the app does
Full-screen WebView · stays alive offline (cache-first when no network, service worker + IndexedDB untouched) ·
pull-to-refresh from the top only · camera permission for barcode/QR · file upload · downloads incl. blob/PDF/CSV/backup files
(saved to Downloads) · window.print() → Android print/PDF · tel/mail/intent links · external sites open in the browser ·
URLs never shown (no address bar, no URL in errors/dialogs) · Back navigates page history (double-back to exit).

## Known limits (please read)
* **Google sign-in (Drive):** Google blocks sign-in inside Android WebViews. The app opens the website's sign-in popup in its
  own window and reports a normal Chrome user agent (`GOOGLE_AUTH_COMPAT_USER_AGENT` in `AppConfig.kt`) to give it the best chance,
  but Google can still refuse (error 403 `disallowed_useragent`). If so, the fix needs a small change in the website
  (redirect/native sign-in) – not done here because the website must stay untouched.
* Web push notifications (`Notification` API) are not supported by Android WebView.
* This project was **not compiled or run** where it was prepared (no Android SDK / internet there). The first GitHub run is the first real build.
