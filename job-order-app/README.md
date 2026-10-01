# Job Orders

A phone-friendly app for a printing shop to create job orders and quotations, take payments,
and send everything on WhatsApp as text or PDF.

## Screens

- **Home**: today at a glance — due today, overdue, in progress, due pending, collected today and
  this month; quick buttons New order, New quotation, Record payment; deliveries due today/overdue
  and recent orders.
- **Orders**: job orders and quotations (switch at the top); search; filters Active, In progress,
  Due today, Overdue, Due pending, Paid, any status, Completed; total due pending.
  **Completed** orders (delivered and fully paid, or cancelled) leave the Active list and go last
  in All, but stay in statements, receipts and reports.
- **Work**: jobs by department (Design, Printing, Finishing, Ready…) with quantities, details and
  notes but no prices, delivery date warnings and a **Move to next stage** button. Each phone
  remembers its department, so the printing phone opens on Printing.
- **＋ New**: job order or quotation — customer, saved or custom items (details in *italics*),
  quantities and prices, optional delivery date / valid-until date, **discount** (SAR or %),
  optional amount paid now, VAT on/off, notes. Totals: subtotal → discount → VAT → total.
- **More**: **Receipts** (every payment with receipt number, e.g. `RCT-A-0001`, period filters,
  totals by method), Customers, Items, Quotations, **Reports**, Settings, Sync & backup.

## Order sheet (tap an order)

Next status in one tap (e.g. *→ Printing*), **Ready message** (tells the customer the job is ready,
with balance), **Reminder** (balance due with bank details), **Call**, **PDF**, **Delivery note**
(PDF without prices, with signature lines; then *Mark as Delivered*), payments (Add payment / Mark
fully paid → receipt), Edit, Duplicate, Delete, Send on WhatsApp.

Quotations (`QT-A-0001`): Send, PDF, **Convert to order** (creates the next job order number and
marks the quote Converted), Declined / Reopen. Quotations are not counted in sales or dues.

## Customers

Call, WhatsApp chat, **Reminder** for all unpaid orders, **Statement** for any period (This month,
Last month, This year, All time or From–To dates) with opening balance, orders, receipts and
closing balance — as PDF or WhatsApp text — new order or quotation, active orders and a
collapsed Completed list.

WhatsApp messages end with the company name in bold and the phone number on the next line.

## Reports

This month / Last month / This year / All time: sales, collected, due pending, average order,
month-by-month table, top customers and top items, **Export to Excel (CSV)** for orders, order
items and receipts.

## Settings

Company name, contact number, address, email, logo, bank name and IBAN (used in reminders and PDFs),
message footer, VAT, default delivery days, order statuses, appearance (Auto/Light/Dark), number
series per device (orders, receipts, quotations), sync, backup.

Dates show as `01-Oct-26`. Money is calculated in halalas and rounded per line.

## Open it

- **Android app:** https://github.com/shabin26-web/shabin/releases/download/job-orders-latest/job-orders.apk
  (see [job-order-android/README.md](../job-order-android/README.md)).
- **Computer:** double-click `job-order-app/index.html`.

## Several devices

Give each device its own series (Settings → This device), e.g. `JO-A`, `JO-B`. Share data through
one common Gmail and a Google Drive folder: [job-order-sync/README.md](../job-order-sync/README.md).
**Update every device to the same app version**: older versions do not understand discounts and
quotations and would show them wrongly.

## Backup

Settings → Backup → **Share backup file** (Android) or **Save backup file** (computer);
**Restore from file** on any device. With sync on, the Drive folder keeps a daily backup for 90 days.
