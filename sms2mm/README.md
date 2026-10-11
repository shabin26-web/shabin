# sms2mm — Bank SMS → Money Manager tracker

An Android app that reads your bank SMS and turns each one into a **Pending** item showing exactly
what to enter in Money Manager: account, category, amount, note
(`SAR 25.42 · Lulu · 🏘️ Household > Home Stationery · STC Pay 💳`).
You type it into Money Manager yourself and tap **Recorded ✔**. The Home screen shows what is
still pending this month, and the total reaches 0 when everything is recorded.

## Security

- **No internet permission.** The app cannot send anything off your phone. CI fails the
  build if `android.permission.INTERNET` ever appears. Check it yourself: Android
  Settings → Apps → SMS → Money Manager → Permissions.
- **Bank senders only.** SMS from senders not on your bank list are not read further.
- **OTP messages are dropped first.** Anything mentioning OTP, verification code,
  password, PIN, "do not share", رمز, التحقق, كلمة المرور or لا تشارك is discarded
  before parsing. Only a count is kept. See `core/src/main/kotlin/sms2mm/core/OtpFilter.kt`.
- **No raw SMS stored.** Only date, amount, merchant, card last-4, account and category,
  in app-private storage that is excluded from phone backups.
- **No Accessibility access.** It never reads or controls other apps. CI fails if an
  Accessibility service ever appears in the manifest.
- **Your data is not in this repo.** Card numbers, account mapping and keywords live in a
  rules file on the phone (Setup → Import / Export rules).

## Install (Samsung / Android phone)

1. **Download**: on the phone, open
   <https://github.com/shabin26-web/shabin/releases/tag/sms2mm-latest> and tap **sms2mm.apk**.
   No GitHub login is needed.
2. **Samsung Auto Blocker**: Settings → Security and privacy → **Auto Blocker → Off**. Turn it back on
   after step 5.
3. **Install**: Google Play Protect blocks apps installed from a file that ask for SMS or
   notification access ("App blocked to protect your device"). Pause it: Play Store → profile →
   **Play Protect → ⚙️ → Scan apps with Play Protect → Off**. Open `sms2mm.apk` from Downloads and
   install it, then turn scanning back on. If it warns about this app later, choose **Keep app**.
4. Save your `sms2mm-rules.json` into Downloads.
5. **Restricted settings**: open the app once, then go to Settings → Apps → **SMS → Money Manager** →
   ⋮ (top right) → **Allow restricted settings**. Android needs this before it lets an app installed
   this way read SMS. (Setup → **Open app info** goes straight there.)
6. **In the app**:
   - **Setup**: Allow SMS + notifications → **Import rules**.
   - **Banks**: **Scan inbox for bank senders** → `+` each sender → **Save**.
7. **Check**: Settings → Apps → SMS → Money Manager → Permissions shows no internet access.

**Updating:** when the repository has the signing secrets below, every build is signed with the same
key, so a new version installs over the old one and keeps your rules and Pending list. Without them,
Android says "App not installed": export your rules, uninstall, install, import again.

**Signing secrets (one-time):** GitHub → repository **Settings → Secrets and variables → Actions →
New repository secret**: `SMS2MM_KEYSTORE_B64` (the base64 keystore) and `SMS2MM_KEYSTORE_PASSWORD`.
Keep the keystore private; anyone with it could sign an app that installs as an update to this one.

## Using it

- **New bank SMS** → notification with the account, category, amount and note →
  enter it in Money Manager → tap **Recorded ✔** (notification, card button, or swipe the card right).
- **Home**:
  - progress ring and "pending this month" per currency
  - **Pending · Recorded · All** filters and 🔍 search
  - swipe right to mark Recorded, swipe left to Ignore
  - tap the amount to copy it, long-press a card to copy all the details
  - **Open Money Manager ↗**
- **Insights**: spent / received / transferred, spending by category, top merchants and per-account counts.
  Browse month by month and switch currency.
- **Rules**:
  - keywords, with several per rule; the longest match wins
  - category, subcategory and account are chosen from **pick lists** built from your Money Manager export
  - transfer rules, ignore words and a test box
- **Setup → Import Money Manager export (.xlsx)** (Money Manager: Settings → Backup → Export to Excel):
  - loads all your accounts, categories and subcategories into the pick lists
  - marks pending items that are already in Money Manager as Recorded: same account, same amount, within a day
- **Setup → Import rules (merge)** adds rules from a file and keeps everything you already have.

## Supported SMS layouts (`core/.../KnownBanks.kt`)

| Bank | Messages |
| --- | --- |
| stc pay | PoS Purchase |
| SNB | Online Purchase, Local Internet purchase, POS Purchase |
| D360 | Local POS Purchase, Incoming Internal transfer |
| Federal Bank (INR) | UPI debit, mandate executed |
| Bank of Baroda (INR) | UPI debit |

An SMS from a trusted sender that matches none of these shows a "not recognised" notification.
Send the masked SMS to get the layout added.

## Develop

```bash
cd sms2mm
./gradlew -p core test        # parser tests, no Android SDK needed
./gradlew :app:assembleDebug  # needs the Android SDK (CI does this)
```

