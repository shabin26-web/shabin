/*
 * Pure business logic for PrintOps: money math, invoice totals, invoice
 * status, receivables aging and the ZATCA QR payload. No DOM access here, so
 * the same file runs in the browser and under `node --test`.
 *
 * All amounts are handled in halalas (integer 1/100 SAR) internally to avoid
 * floating-point drift, and converted back to SAR only for display.
 */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.Core = factory();
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const JOB_STATUSES = [
    "Quote",
    "Approved",
    "Prepress",
    "Printing",
    "Finishing",
    "Ready",
    "Delivered",
    "Cancelled",
  ];
  const OPEN_JOB_STATUSES = ["Approved", "Prepress", "Printing", "Finishing", "Ready"];
  const PAYMENT_METHODS = ["Bank transfer", "Cash", "Card / mada", "Cheque", "Other"];
  /** Production steps in order; an order is complete when it reaches the last one. */
  const STAGES = JOB_STATUSES.filter((s) => s !== "Cancelled");

  /** Where an order stands: step n of 7, percent complete, and the next step. */
  function jobProgress(status) {
    const i = STAGES.indexOf(status);
    if (i < 0) return { step: 0, of: STAGES.length, pct: 0, next: null, cancelled: true };
    return {
      step: i + 1,
      of: STAGES.length,
      pct: Math.round((i / (STAGES.length - 1)) * 100),
      next: STAGES[i + 1] || null,
      cancelled: false,
    };
  }

  /**
   * Phone number in the international digits-only form WhatsApp links need.
   * Local Saudi mobiles (05XXXXXXXX or 5XXXXXXXX) get the 966 country code.
   */
  function waPhone(phone) {
    let d = String(phone || "").replace(/\D/g, "");
    if (d.startsWith("00")) d = d.slice(2);
    if (d.length === 10 && d.startsWith("05")) d = "966" + d.slice(1);
    else if (d.length === 9 && d.startsWith("5")) d = "966" + d;
    return d.length >= 8 ? d : "";
  }

  /** SAR number -> integer halalas, rounded half away from zero. */
  function toHalalas(sar) {
    const n = Number(sar);
    if (!Number.isFinite(n)) return 0;
    return Math.sign(n) * Math.round(Math.abs(n) * 100);
  }

  /** Integer halalas -> SAR number with 2 decimals. */
  function toSar(halalas) {
    return Math.round(halalas) / 100;
  }

  /** Format halalas as "12,345.67". */
  function fmt(halalas) {
    const v = toSar(halalas);
    return v.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  /** Multiply a SAR unit price by a quantity and return halalas, rounded per line. */
  function lineAmount(qty, unitPrice) {
    const q = Number(qty) || 0;
    const p = Number(unitPrice) || 0;
    return Math.sign(q * p) * Math.round(Math.abs(q * p) * 100);
  }

  /**
   * Totals for an invoice. Discount is applied before VAT (VAT is charged on
   * the net taxable amount). Every step is rounded to the halala.
   */
  function invoiceTotals(invoice) {
    const lines = invoice.lines || [];
    const subtotal = lines.reduce((s, l) => s + lineAmount(l.qty, l.unitPrice), 0);
    const discount = Math.min(toHalalas(invoice.discount || 0), subtotal);
    const taxable = subtotal - discount;
    const rate = Number(invoice.vatRate ?? 15);
    const vat = Math.round((taxable * rate) / 100);
    const total = taxable + vat;
    return { subtotal, discount, taxable, vat, total, rate };
  }

  function paidFor(invoiceId, payments) {
    return payments
      .filter((p) => p.invoiceId === invoiceId)
      .reduce((s, p) => s + toHalalas(p.amount), 0);
  }

  function daysBetween(fromIso, toIso) {
    const a = Date.parse(fromIso + "T00:00:00Z");
    const b = Date.parse(toIso + "T00:00:00Z");
    return Math.round((b - a) / 86400000);
  }

  /**
   * Status of an invoice as of `today` (ISO date):
   * Draft | Void | Paid | Partial | Overdue | Unpaid.
   */
  function invoiceStatus(invoice, payments, today) {
    if (invoice.void) return "Void";
    if (invoice.draft) return "Draft";
    const { total } = invoiceTotals(invoice);
    const paid = paidFor(invoice.id, payments);
    const balance = total - paid;
    if (balance <= 0) return "Paid";
    if (invoice.dueDate && daysBetween(invoice.dueDate, today) > 0) return "Overdue";
    if (paid > 0) return "Partial";
    return "Unpaid";
  }

  /** Invoices that count toward receivables (issued, not void). */
  function isIssued(invoice) {
    return !invoice.void && !invoice.draft;
  }

  /**
   * Receivables aging by days past due date. Returns buckets in halalas plus
   * a total that must equal the sum of all open balances.
   */
  function aging(invoices, payments, today) {
    const buckets = { current: 0, d1_30: 0, d31_60: 0, d61_90: 0, d90p: 0 };
    let total = 0;
    for (const inv of invoices) {
      if (!isIssued(inv)) continue;
      const bal = invoiceTotals(inv).total - paidFor(inv.id, payments);
      if (bal <= 0) continue;
      total += bal;
      const late = inv.dueDate ? daysBetween(inv.dueDate, today) : 0;
      if (late <= 0) buckets.current += bal;
      else if (late <= 30) buckets.d1_30 += bal;
      else if (late <= 60) buckets.d31_60 += bal;
      else if (late <= 90) buckets.d61_90 += bal;
      else buckets.d90p += bal;
    }
    return { ...buckets, total };
  }

  /**
   * Tie-out check: invoiced − collected (on issued invoices) must equal the
   * outstanding receivables, and payments must never exceed invoice totals.
   */
  function reconcile(invoices, payments) {
    const issued = invoices.filter(isIssued);
    const issuedIds = new Set(issued.map((i) => i.id));
    const invoiced = issued.reduce((s, i) => s + invoiceTotals(i).total, 0);
    const collected = payments
      .filter((p) => issuedIds.has(p.invoiceId))
      .reduce((s, p) => s + toHalalas(p.amount), 0);
    const outstanding = issued.reduce(
      (s, i) => s + (invoiceTotals(i).total - paidFor(i.id, payments)),
      0,
    );
    const orphanPayments = payments.filter((p) => !issuedIds.has(p.invoiceId));
    const overpaid = issued.filter((i) => paidFor(i.id, payments) > invoiceTotals(i).total);
    return {
      invoiced,
      collected,
      outstanding,
      residual: invoiced - collected - outstanding,
      orphanPayments,
      overpaid,
    };
  }

  /* ---------- ZATCA simplified-invoice QR (TLV, base64) ---------- */

  function utf8Bytes(str) {
    if (typeof TextEncoder !== "undefined") return Array.from(new TextEncoder().encode(str));
    return Array.from(Buffer.from(str, "utf8"));
  }

  function bytesToBase64(bytes) {
    if (typeof btoa === "function") {
      let bin = "";
      for (const b of bytes) bin += String.fromCharCode(b);
      return btoa(bin);
    }
    return Buffer.from(bytes).toString("base64");
  }

  /**
   * TLV-encoded QR payload with tags 1–5 (seller name, VAT number, timestamp,
   * invoice total incl. VAT, VAT total). This is the Phase 1 (generation)
   * format; Phase 2 (integration) additionally requires a cryptographic stamp
   * issued via the Fatoora platform, which a browser-only app cannot provide.
   */
  function zatcaTlv({ sellerName, vatNumber, timestamp, total, vat }) {
    const fields = [sellerName, vatNumber, timestamp, total, vat];
    const out = [];
    fields.forEach((value, i) => {
      const bytes = utf8Bytes(String(value ?? ""));
      out.push(i + 1, bytes.length, ...bytes);
    });
    return bytesToBase64(out);
  }

  /** Saudi VAT registration numbers are 15 digits, starting and ending with 3. */
  function isValidVatNumber(v) {
    return /^3\d{13}3$/.test(String(v || "").trim());
  }

  return {
    JOB_STATUSES,
    OPEN_JOB_STATUSES,
    PAYMENT_METHODS,
    STAGES,
    jobProgress,
    waPhone,
    toHalalas,
    toSar,
    fmt,
    lineAmount,
    invoiceTotals,
    paidFor,
    daysBetween,
    invoiceStatus,
    isIssued,
    aging,
    reconcile,
    zatcaTlv,
    isValidVatNumber,
  };
});
