# Job Orders (Job Order Sender)

A phone-friendly app for a printing shop to create job orders and send them on WhatsApp.

1. **Items**: save products once (name, details such as size/paper/colours, unit, default price).
   Details show in *italics* in the app and in WhatsApp.
2. **Customers**: name, WhatsApp number, company, notes. Tap a customer for all previous orders
   and how much they owe.
3. **New order**: pick a customer, add items, quantities and prices, an optional delivery date,
   an optional amount paid now (cash, bank transfer, card/mada), notes, VAT on/off.
4. **Save & send on WhatsApp**: the order gets this device's next number (e.g. `JO-A-0001`) and
   opens the message to edit, copy or send.
5. **Orders**: search; filter by status, **Due pending** or **Paid**; total due at the top.
   Tap an order to change status, **Add payment**, **Mark fully paid**, send again, edit,
   duplicate or delete.

6. **Receipts**: every payment gets a receipt number (e.g. `RCT-A-0001`, per device). The
   Receipts tab lists them for Today / This month / Last month / All with totals by payment method;
   tap one for the receipt details and **Send receipt** on WhatsApp (amount, method, order, paid to
   date and balance).

Dates show as `01-Oct-26`. Statuses (New → Design → Waiting approval → Approved → Printing →
Finishing → Ready → Delivered, On hold, Cancelled) can be renamed, added and reordered in
Settings. Appearance can be Auto, Light or Dark.

## Open it

- **Android app:** download
  https://github.com/shabin26-web/shabin/releases/download/job-orders-latest/job-orders.apk
  on the phone and tap Install. See [job-order-android/README.md](../job-order-android/README.md).
- **Computer:** double-click `job-order-app/index.html`.

## Several devices

- Give each device its own **order number series** (Settings → This device), e.g. `JO-A`, `JO-B`.
- To share the same data on all devices, set up **sync** with one common Gmail and a Google Drive
  folder: [job-order-sync/README.md](../job-order-sync/README.md).

## Backup

**Settings → Backup → Share backup file** (Android) or **Save backup file** (computer) creates
`job-orders-backup-01-Oct-26.json`. Keep it in Google Drive, WhatsApp or email.
**Restore from file** loads it on any device. 1,000 orders make a file of about 0.6 MB, and the
device can hold several thousand orders; Settings shows how much space is used.
With sync on, the Drive folder also keeps a daily backup for 90 days.

Money is calculated in halalas and rounded per line; VAT is charged on the subtotal.
