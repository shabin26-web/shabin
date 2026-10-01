# Job Orders — Android app

This folder turns `job-order-app/index.html` into an installable Android app (Capacitor).
The APK is built automatically by GitHub Actions (`.github/workflows/android-apk.yml`) every time
the app changes, and published here:

**Download:** https://github.com/shabin26-web/shabin/releases/download/job-orders-latest/job-orders.apk

## Install on an Android phone

1. Open the download link above in Chrome on the phone.
2. When it finishes, tap **Open** (or open it from *Downloads*).
3. The first time, Android asks to allow installs from Chrome: tap **Settings → Allow from this
   source**, then go back.
4. Tap **Install**. The **Job Orders** icon appears on the home screen.
5. Play Protect may warn that the app is from an unknown developer (it is not from the Play
   Store). Tap **More details → Install anyway**.

## Updates

Download and install the APK again from the same link. It installs over the old version and keeps
your orders, customers and items (older data is upgraded automatically).
**Uninstalling the app deletes its data**, so use *Settings → Share backup file* first
(save it to Google Drive or send it to yourself on WhatsApp), or turn on sync.

## In the app

- The app starts below the phone's status bar (time and notifications); the status-bar icons follow
  the Light/Dark setting.

- **Open WhatsApp** opens the WhatsApp app with the message ready for the customer's number.
- **Other apps** shares the order text to any app (Telegram, SMS, email…).
- **PDF** (on an order or a receipt) creates an A5 PDF and **Share PDF** sends it; in WhatsApp
  you then pick the chat (Android does not let apps pre-select the contact for files).
- **Share backup file** saves all your data as a file to Google Drive, WhatsApp or email.
- **Sync** shares the same data across phones through one common Gmail: see
  [job-order-sync/README.md](../job-order-sync/README.md).
- The phone's **Back** button closes a window, then returns to *New order*, then exits.

## For developers

```bash
cd job-order-android
npm ci
npm run copy-web            # copy ../job-order-app/index.html into www/
npx cap add android         # android/ is generated, not committed
npx capacitor-assets generate --android
npx cap sync android
cd android && ./gradlew assembleRelease   # needs the Android SDK
```

`signing/job-orders.keystore` signs every build with the same key so updates install over each
other. It is a sideloading key kept in the repo on purpose (password `joborders-sideload`);
create a private key stored as a GitHub secret before any Play Store release.
