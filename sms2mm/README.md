# sms2mm — Bank SMS → Money Manager

An Android app that reads your bank SMS, shows each transaction as a
notification (`SAR 45.00 · Panda · Groceries`), and on **Add** fills the entry
into Money Manager for you.

> Status: **step 1 of 5** — the parsing core is done and tested. The Android app,
> Money Manager auto-fill and APK build come next.

## Security

- **No internet permission.** The app cannot send anything off your phone. You can
  check this yourself in Android Settings → Apps → sms2mm → Permissions.
- **Bank senders only.** SMS from senders not in your bank list are not read further.
- **OTP messages are dropped first.** Anything mentioning OTP, verification code,
  password, PIN, "do not share", رمز, التحقق, كلمة المرور or لا تشارك is
  discarded before parsing. Only the bank name and time are counted, so you know
  a message was skipped. See `core/src/main/kotlin/sms2mm/core/OtpFilter.kt`.
- **No raw SMS stored.** Only date, amount, type, merchant, card last-4 and category.

## Layout

| Path | What it is |
| --- | --- |
| `core/` | Plain Kotlin: OTP filter, bank rules, parser, categories, dedup. Unit-tested. |
| `app/` | *(next step)* Android app: SMS receiver, notification, Money Manager auto-fill. |

## Run the tests

```bash
cd sms2mm
./gradlew :core:test
```

## How a bank SMS is handled (`SmsProcessor`)

1. The sender isn't one of your banks → ignored.
2. It looks like an OTP or password → dropped (`Sensitive`).
3. It matches an ignore keyword (promotion, declined) → `Ignored`.
4. It matches a bank pattern → amount, merchant and card are extracted. Arabic-Indic digits (٤٥٫٧٥) are supported.
5. The merchant is matched against your keyword list to set the category; the card last-4 sets the Money Manager account.
6. It matches nothing → `Unparsed`, listed so you can add it by hand.
