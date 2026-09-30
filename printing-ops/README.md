# PrintOps Ledger

A browser-based system for a printing company to track **jobs** (quote → production → delivery),
issue **VAT invoices**, record **customer payments**, and keep receivables reconciled. Amounts are in SAR.

No server, database or install is needed. It is plain HTML, CSS and JavaScript.

## Open it

- **On your computer:** open `printing-ops/index.html` in Chrome, Edge or Safari.
- **On the web (GitHub Pages):** in the repo, go to *Settings → Pages*, choose *Deploy from a branch*,
  pick the branch and `/ (root)`, and save. The app will be at
  `https://<user>.github.io/<repo>/printing-ops/`.

It opens with **sample data** so you can explore. When you are ready, go to **Settings**, enter
your company details (name, VAT number, CR, IBAN), then click **Clear sample data and start**.

## What it does

| Area | Features |
| --- | --- |
| Dashboard | Jobs in production, late jobs, receivables, overdue, invoiced and collected this month, production pipeline, aging buckets, **Orders to complete** with progress bars and a one-click next-stage button, recent payments |
| Jobs | Job number, customer, product, quantity, size, paper, colours (4/4, 4/0…), finishing, due date, price and estimated cost (margin); status from Quote → Approved → Prepress → Printing → Finishing → Ready → Delivered; one-click **Invoice** from a job |
| Order details | Click any order: 7-step completion tracker with the date each stage was reached, full spec, amount incl. VAT, invoice and payment status, internal cost and margin, and an activity log of every status change, invoice and payment. **Move to next stage** / **Mark delivered (complete)** |
| Invoices | Draft → Issue (sequential numbers `INV-YYYY-0001`), multi-line, discount before VAT, 15% or 0% VAT, bilingual Tax / Simplified Tax Invoice layout with ZATCA QR, print or save as PDF, void (issued invoices are locked) |
| Payments | Receipt numbers `RCT-0001`, method (bank transfer, cash, card/mada, cheque), reference; overpayment is blocked |
| Customers | Click a customer to open their page: all previous orders with progress, invoices, balance due and overdue, and a combined activity history. VAT number validation (15 digits, starts and ends with 3), statement with running balance and tie-out check, statement CSV |
| WhatsApp sharing | **Share on WhatsApp** on orders, invoices and customer pages: editable message text, **Copy text**, or **Open WhatsApp** straight to the customer's number (Saudi numbers such as 05XXXXXXXX are converted to 9665XXXXXXXX). Cost and margin are never included |
| Reports | Receivables tie-out (invoiced − collected = open balances = aging total, residual 0.00), aging by customer, output VAT by month, collections by method, delivered-job gross margin, CSV exports |
| Settings | Company details printed on invoices, invoice prefix, payment terms, JSON backup export / import |

## Accounting rules built in

- All money is calculated in halalas (whole numbers) and rounded per line, so totals never drift.
- VAT is charged on the amount after discount: `Taxable = Subtotal − Discount`, `VAT = Taxable × rate`,
  `Total = Taxable + VAT`.
- Receivables count **issued** invoices only; drafts and void invoices are excluded.
- A customer with a VAT number gets a **Tax Invoice**; one without gets a **Simplified Tax Invoice**.
- The Reports page shows a ✓ / ✗ tie-out so any mismatch is visible immediately.

## Limitations to know

- **Data lives in the browser** (localStorage) on the computer where it is entered. It is not shared
  between users or devices. Export a backup regularly from Settings.
- The QR code follows the **ZATCA Phase 1** (generation) TLV format. **Phase 2** (integration) requires
  invoices to be cleared or reported through the Fatoora platform with a cryptographic stamp and UUID,
  which needs a certified e-invoicing solution. Use this tool for operations tracking, or confirm your
  Phase 2 wave obligations before using its invoices as your legal tax invoices.
- There are no credit notes yet. Voiding is for invoices issued in error; corrections to invoices
  already sent to the customer should normally be made with a credit note.
- Input VAT on purchases and expenses is not tracked, so the VAT report is the sales (output) side only.

## Files

```
printing-ops/
├── index.html          page shell
├── css/styles.css      styles (light and dark, print layout for A4 invoices)
├── js/core.js          money math, invoice status, aging, reconciliation, ZATCA QR payload
├── js/app.js           screens and forms
├── js/vendor/          QR code library (MIT), bundled so it works offline
└── tests/core.test.js  unit tests for core.js
```

Run the tests with Node 18+:

```bash
node --test printing-ops/tests/*.test.js
```
