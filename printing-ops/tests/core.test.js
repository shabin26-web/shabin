// Run with:  node --test printing-ops/tests/*.test.js
const test = require("node:test");
const assert = require("node:assert/strict");
const Core = require("../js/core.js");

const inv = (over = {}) => ({
  id: "i1",
  lines: [
    { desc: "Business cards", qty: 1000, unitPrice: 0.15 },
    { desc: "Design", qty: 1, unitPrice: 200 },
  ],
  discount: 0,
  vatRate: 15,
  date: "2026-09-01",
  dueDate: "2026-09-30",
  ...over,
});

test("toHalalas rounds to the halala and avoids float drift", () => {
  assert.equal(Core.toHalalas(0.1 + 0.2), 30);
  assert.equal(Core.toHalalas("1,000"), 0); // not a number -> 0, never NaN
  assert.equal(Core.toHalalas(12.345), 1235);
  assert.equal(Core.toHalalas(-12.345), -1235);
});

test("invoiceTotals: subtotal, discount before VAT, 15% VAT", () => {
  const t = Core.invoiceTotals(inv({ discount: 50 }));
  assert.equal(t.subtotal, 35000); // 150 + 200
  assert.equal(t.discount, 5000);
  assert.equal(t.taxable, 30000);
  assert.equal(t.vat, 4500);
  assert.equal(t.total, 34500);
  assert.equal(t.taxable + t.vat, t.total);
});

test("discount can never exceed subtotal", () => {
  const t = Core.invoiceTotals(inv({ discount: 99999 }));
  assert.equal(t.taxable, 0);
  assert.equal(t.total, 0);
});

test("invoiceStatus transitions", () => {
  const i = inv();
  assert.equal(Core.invoiceStatus(i, [], "2026-09-15"), "Unpaid");
  assert.equal(Core.invoiceStatus(i, [], "2026-10-01"), "Overdue");
  const part = [{ invoiceId: "i1", amount: 100 }];
  assert.equal(Core.invoiceStatus(i, part, "2026-09-15"), "Partial");
  const full = [{ invoiceId: "i1", amount: 402.5 }];
  assert.equal(Core.invoiceStatus(i, full, "2026-12-31"), "Paid");
  assert.equal(Core.invoiceStatus({ ...i, draft: true }, [], "2026-12-31"), "Draft");
  assert.equal(Core.invoiceStatus({ ...i, void: true }, [], "2026-12-31"), "Void");
});

test("aging buckets sum to total and reconcile has zero residual", () => {
  const invoices = [
    inv({ id: "a", dueDate: "2026-10-10" }), // current
    inv({ id: "b", dueDate: "2026-09-20" }), // 10 days late
    inv({ id: "c", dueDate: "2026-07-01" }), // 91 days late
    inv({ id: "d", draft: true }), // excluded
    inv({ id: "e", void: true }), // excluded
  ];
  const payments = [
    { invoiceId: "b", amount: 100 },
    { invoiceId: "c", amount: 402.5 }, // fully paid
  ];
  const a = Core.aging(invoices, payments, "2026-09-30");
  const sum = a.current + a.d1_30 + a.d31_60 + a.d61_90 + a.d90p;
  assert.equal(sum, a.total);
  assert.equal(a.current, 40250);
  assert.equal(a.d1_30, 30250);
  assert.equal(a.d90p, 0);

  const r = Core.reconcile(invoices, payments);
  assert.equal(r.invoiced, 40250 * 3);
  assert.equal(r.collected, 50250);
  assert.equal(r.outstanding, a.total);
  assert.equal(r.residual, 0);
  assert.equal(r.orphanPayments.length, 0);
  assert.equal(r.overpaid.length, 0);
});

test("reconcile flags orphan and overpaid payments", () => {
  const r = Core.reconcile(
    [inv({ id: "a" })],
    [
      { invoiceId: "a", amount: 500 },
      { invoiceId: "missing", amount: 10 },
    ],
  );
  assert.equal(r.overpaid.length, 1);
  assert.equal(r.orphanPayments.length, 1);
});

test("ZATCA TLV encodes the 5 tags in order", () => {
  const b64 = Core.zatcaTlv({
    sellerName: "Acme",
    vatNumber: "300000000000003",
    timestamp: "2026-09-30T10:00:00Z",
    total: "115.00",
    vat: "15.00",
  });
  const bytes = Buffer.from(b64, "base64");
  assert.equal(bytes[0], 1);
  assert.equal(bytes[1], 4);
  assert.equal(bytes.subarray(2, 6).toString(), "Acme");
  assert.equal(bytes[6], 2);
  assert.equal(bytes[7], 15);
});

test("Saudi VAT number validation", () => {
  assert.ok(Core.isValidVatNumber("300000000000003"));
  assert.ok(!Core.isValidVatNumber("30000000000000"));
  assert.ok(!Core.isValidVatNumber("100000000000003"));
});

test("jobProgress walks the production stages", () => {
  assert.deepEqual(Core.jobProgress("Quote"), { step: 1, of: 7, pct: 0, next: "Approved", cancelled: false });
  assert.equal(Core.jobProgress("Printing").pct, 50);
  assert.equal(Core.jobProgress("Printing").next, "Finishing");
  const done = Core.jobProgress("Delivered");
  assert.equal(done.pct, 100);
  assert.equal(done.next, null);
  assert.equal(Core.jobProgress("Cancelled").cancelled, true);
});

test("waPhone normalises Saudi numbers for WhatsApp links", () => {
  assert.equal(Core.waPhone("+966 55 000 0001"), "966550000001");
  assert.equal(Core.waPhone("0551234567"), "966551234567");
  assert.equal(Core.waPhone("551234567"), "966551234567");
  assert.equal(Core.waPhone("00966551234567"), "966551234567");
  assert.equal(Core.waPhone(""), "");
});
