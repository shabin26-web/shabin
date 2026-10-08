# sms2mm — Bank SMS → Money Manager

An Android app that reads your bank SMS, shows each transaction as a
notification (`SAR 25.42 · Lulu · 🏘️ Household > Home Stationery · STC Pay 💳`),
and on **Add** opens Money Manager and fills the entry in. You check it and tap **Save**.

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
- **Auto-fill sees Money Manager only.** The Accessibility service is limited to
  Money Manager's package (`res/xml/accessibility_service.xml`) and never taps Save.
- **Your data is not in this repo.** Card numbers, account mapping and keywords live in a
  rules file on the phone (Setup → Import / Export rules).

## Install (Samsung / Android phone)

1. **Download**: on the phone, open
   <https://github.com/shabin26-web/shabin/releases/tag/sms2mm-latest> and tap **sms2mm.apk**.
   No GitHub login is needed.
2. **Samsung Auto Blocker**: Settings → Security and privacy → **Auto Blocker → Off**. Turn it back on
   after step 5.
3. **Install**: open `sms2mm.apk` from Downloads. If asked, allow installing from this source.
   If Play Protect warns, tap **More details → Install anyway**. It warns because the app reads
   SMS and isn't from the Play Store, which doesn't allow SMS-reading apps like this.
4. Save your `sms2mm-rules.json` into Downloads.
5. **Restricted settings**: open the app once, then go to Settings → Apps → **SMS → Money Manager** →
   ⋮ (top right) → **Allow restricted settings**. Android needs this before it lets an app installed
   this way use SMS or Accessibility.
6. **In the app**:
   - **Setup**: Allow SMS + notifications → Open Accessibility settings → Installed apps →
     **SMS → Money Manager auto-fill → On** → **Import rules**.
   - **Banks**: **Scan inbox for bank senders** → `+` each sender → **Save**.
7. **Check**: Settings → Apps → SMS → Money Manager → Permissions shows no internet access.

**Updating:** before installing a newer build, **Setup → Export rules** first. Each CI build is signed
with a temporary key, so Android may refuse to install it over the old version ("App not installed").
If that happens, uninstall the old version, install the new one, and **Import rules** again.
The Pending list starts empty after a reinstall.

## Using it

- **New bank SMS** → notification → **Add** → Money Manager opens and fills the tab,
  account, category, amount and note → you tap **Save**.
- **No category known** (e.g. UPI to a person) → the notification says **Choose category**.
  Pick one and tick **Remember … for next time** to teach the app that merchant.
- **Pending tab** → this month's tie-out per currency: captured − entered − ignored
  = still to add, red until it is 0. It also shows how many OTP or unrecognised SMS were skipped.
- **Keywords tab** → change rules any time:
  - one rule can have **several keywords** (`LULU`, `LULU HYPER`, `لولو`); the longest match wins
  - optionally limit a rule to one account or to expense/income only
  - **transfer rules**: payer/payee → Money Manager transfer with another account
  - **ignore words**
  - **test box** to see which rule a merchant name hits

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

If the auto-fill gets stuck on a Money Manager screen: Setup → **Record Money Manager's add
screen**, tap Add once, then **Share recorded layout** so the field matching can be adjusted.
