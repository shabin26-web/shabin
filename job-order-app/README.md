# Job Order Sender

A simple phone-friendly app for a printing shop:

1. **Items**: save your products once (name, details such as size/paper/colours, unit, default price).
2. **Customers**: save name, WhatsApp number, company and notes.
3. **New order**: pick a customer, add saved items (or a custom item), set quantity and price,
   delivery date, advance paid, notes, VAT on/off. Totals update as you type.
4. **Save & send on WhatsApp**: the order gets a number (`JO-0001`, `JO-0002`, …) and opens a
   message you can edit, then **Copy text** or **Open WhatsApp** straight to the customer's number
   (Saudi numbers like `05XXXXXXXX` are converted to `9665XXXXXXXX` automatically).
5. **Orders**: search, filter by status (New → In progress → Ready → Delivered), send again,
   edit, duplicate or delete. Tap a customer to see all their previous orders.

## Open it

- **Android app:** download
  https://github.com/shabin26-web/shabin/releases/download/job-orders-latest/job-orders.apk
  on the phone and tap Install. See [job-order-android/README.md](../job-order-android/README.md).

- **Computer:** double-click `job-order-app/index.html`.
- **Phone:** turn on GitHub Pages for this branch (repo *Settings → Pages*), then open
  `https://shabin26-web.github.io/shabin/job-order-app/` and use *Add to Home Screen*.

## Your data

Data is saved on the device you use (browser storage). It is not shared between phones.
Go to **Settings → Copy backup** regularly and keep the text somewhere safe (a WhatsApp message to
yourself, email or Google Drive). Paste it into **Settings → Restore** to move to another device.

Money is calculated in halalas and rounded per line; VAT is charged on the subtotal.
