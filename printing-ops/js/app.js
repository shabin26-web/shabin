/* PrintOps Ledger — UI layer. All money math lives in core.js. */
(function () {
  "use strict";

  const C = window.Core;
  const STORE_KEY = "printops.v1";

  /* ================= helpers ================= */
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => Array.from(el.querySelectorAll(s));
  const esc = (s) =>
    String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  const uid = () =>
    (crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2));

  function todayIso() {
    const d = new Date();
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    return d.toISOString().slice(0, 10);
  }
  function addDays(iso, n) {
    const d = new Date(iso + "T00:00:00Z");
    d.setUTCDate(d.getUTCDate() + Number(n || 0));
    return d.toISOString().slice(0, 10);
  }
  function fmtDate(iso) {
    if (!iso) return "—";
    const [y, m, d] = iso.split("-");
    return `${d}/${m}/${y}`;
  }
  const sar = (h) => C.fmt(h);
  const pad = (n, w = 4) => String(n).padStart(w, "0");

  /* ================= state ================= */
  const DEFAULT_SETTINGS = {
    companyName: "Your Printing Co.",
    companyNameAr: "",
    vatNumber: "",
    crNumber: "",
    address: "Jeddah, Kingdom of Saudi Arabia",
    phone: "",
    email: "",
    iban: "",
    bankName: "",
    invoicePrefix: "INV",
    paymentTerms: 30,
    vatRate: 15,
  };

  function emptyState() {
    return {
      settings: { ...DEFAULT_SETTINGS },
      customers: [],
      jobs: [],
      invoices: [],
      payments: [],
      seq: { job: 0, invoice: 0, receipt: 0 },
      demo: false,
    };
  }

  let storageOk = true;
  let state = load();
  // Persist sample data on first run so record links (e.g. #customer/<id>) survive a reload.
  try {
    if (!localStorage.getItem(STORE_KEY)) localStorage.setItem(STORE_KEY, JSON.stringify(state));
  } catch (e) {
    storageOk = false;
  }

  function load() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (raw) {
        const s = JSON.parse(raw);
        return { ...emptyState(), ...s, settings: { ...DEFAULT_SETTINGS, ...s.settings } };
      }
    } catch (e) {
      storageOk = false;
    }
    return demoState();
  }

  function save() {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(state));
      storageOk = true;
    } catch (e) {
      storageOk = false;
    }
    renderBanner();
  }

  const byId = (arr, id) => arr.find((x) => x.id === id);
  const customer = (id) => byId(state.customers, id) || { name: "(deleted customer)" };
  const invStatus = (inv) => C.invoiceStatus(inv, state.payments, todayIso());
  const invTotals = (inv) => C.invoiceTotals(inv);
  const invPaid = (inv) => C.paidFor(inv.id, state.payments);
  const invBalance = (inv) => invTotals(inv).total - invPaid(inv);
  const money = (h) => `SAR ${sar(h)}`;

  /* ---------- order history & activity ---------- */
  function logJob(job, text) {
    if (!job.history) job.history = [];
    job.history.push({ at: new Date().toISOString(), text });
  }
  function setStatus(job, status) {
    if (!status || job.status === status) return false;
    logJob(job, `Status changed: ${job.status} → ${status}`);
    job.status = status;
    return true;
  }
  function jobHistory(j) {
    return j.history?.length ? j.history : [{ at: (j.createdAt || todayIso()) + "T09:00:00", text: `Order created — ${j.status}` }];
  }
  function fmtStamp(at) {
    if (!at || at.length <= 10) return fmtDate(at);
    const d = new Date(at);
    if (isNaN(d)) return fmtDate(at.slice(0, 10));
    const iso = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString();
    return `${fmtDate(iso.slice(0, 10))} ${iso.slice(11, 16)}`;
  }
  /** Everything that happened to these orders and invoices, newest first. */
  function activityFor(jobs, invoices) {
    const ev = [];
    for (const j of jobs) {
      for (const h of jobHistory(j)) ev.push({ at: h.at, kind: /Delivered$/.test(h.text) ? "done" : "job", text: h.text, ref: j.jobNo, act: "view-job", id: j.id });
    }
    for (const i of invoices) {
      if (i.draft) continue;
      ev.push({ at: i.issuedAt || i.date, kind: "invoice", text: `Invoice issued — ${money(invTotals(i).total)} incl. VAT`, ref: i.number, act: "view-invoice", id: i.id });
      if (i.void) ev.push({ at: i.voidedAt || i.issuedAt || i.date, kind: "void", text: "Invoice voided", ref: i.number, act: "view-invoice", id: i.id });
      for (const p of state.payments.filter((x) => x.invoiceId === i.id)) {
        ev.push({ at: p.date + "T12:00:00", kind: "payment", text: `Payment received — ${money(C.toHalalas(p.amount))} by ${p.method}${p.reference ? " (" + p.reference + ")" : ""}`, ref: p.receiptNo, act: "view-invoice", id: i.id });
      }
    }
    return ev.sort((a, b) => (Date.parse(b.at) || 0) - (Date.parse(a.at) || 0));
  }
  function timeline(events, limit = 60) {
    if (!events.length) return `<div class="empty">No activity yet.</div>`;
    return `<ol class="timeline">${events.slice(0, limit).map((e) => `
      <li class="tl-${e.kind}"><span class="tl-when">${fmtStamp(e.at)}</span>
        <span class="tl-what"><button type="button" class="link-btn mono" data-act="${e.act}" data-id="${e.id}">${esc(e.ref)}</button> ${esc(e.text)}</span></li>`).join("")}</ol>`;
  }
  const jobInvoices = (j) => state.invoices.filter((i) => (i.jobIds || []).includes(j.id) || i.id === j.invoiceId);

  function progressBar(j, compact = false) {
    const p = C.jobProgress(j.status);
    if (p.cancelled) return `<div class="progress cancelled" title="Cancelled"><span style="width:0"></span></div>${compact ? "" : `<span class="small muted">Cancelled</span>`}`;
    return `<div class="progress ${p.pct === 100 ? "complete" : ""}" role="progressbar" aria-valuenow="${p.pct}" aria-valuemin="0" aria-valuemax="100" aria-label="${esc(j.jobNo)} ${p.pct}% complete"><span style="width:${p.pct}%"></span></div>
      <span class="small muted">${compact ? `${p.step}/${p.of}` : `Step ${p.step} of ${p.of} · ${p.pct}% complete`}</span>`;
  }
  function stepper(j) {
    const p = C.jobProgress(j.status);
    const reached = new Map();
    for (const h of jobHistory(j)) {
      const m = h.text.match(/→ (\w+)$/) || h.text.match(/^Order created — (\w+)$/);
      if (m && !reached.has(m[1])) reached.set(m[1], h.at);
    }
    return `<ol class="stepper ${p.cancelled ? "is-cancelled" : ""}">${C.STAGES.map((st, k) => {
      const cls = p.cancelled ? "" : k + 1 < p.step ? "done" : k + 1 === p.step ? (p.pct === 100 ? "done" : "current") : "";
      return `<li class="${cls}"><span class="dot">${cls === "done" ? "✓" : k + 1}</span><span class="lbl">${st}</span>${reached.has(st) && cls ? `<span class="when">${fmtStamp(reached.get(st)).slice(0, 5)}</span>` : ""}</li>`;
    }).join("")}</ol>`;
  }
  function nextStepButton(j, cls = "btn primary") {
    const p = C.jobProgress(j.status);
    if (!p.next) return "";
    const label = p.next === "Delivered" ? "Mark delivered (complete)" : `Move to ${p.next}`;
    return `<button type="button" class="${cls}" data-act="advance-job" data-id="${j.id}">${label}</button>`;
  }

  /* ---------- WhatsApp text ---------- */
  function signature() {
    const s = state.settings;
    return `\n— ${s.companyName}${s.phone ? "\n" + s.phone : ""}`;
  }
  function jobShareText(j) {
    const c = customer(j.customerId);
    const p = C.jobProgress(j.status);
    const inv = j.invoiceId && byId(state.invoices, j.invoiceId);
    const t = inv && C.isIssued(inv) ? invTotals(inv) : C.invoiceTotals({ lines: [{ qty: 1, unitPrice: j.price }], vatRate: state.settings.vatRate });
    const lines = [
      `*Order ${j.jobNo}* — ${j.title}`,
      `Customer: ${c.name}`,
      "",
      `Product: ${j.product || "—"}`,
      `Quantity: ${Number(j.qty || 0).toLocaleString("en-US")}`,
      j.size ? `Size: ${j.size}` : null,
      j.material ? `Paper / material: ${j.material}` : null,
      j.colors ? `Colours: ${j.colors}` : null,
      j.finishing ? `Finishing: ${j.finishing}` : null,
      "",
      `Status: *${j.status}*${p.cancelled ? "" : ` (step ${p.step} of ${p.of}, ${p.pct}% complete)`}`,
      j.dueDate ? `Due date: ${fmtDate(j.dueDate)}` : null,
      "",
      `Amount: ${money(t.taxable)} + VAT ${t.rate}% ${sar(t.vat)} = *${money(t.total)}*`,
    ];
    if (inv && C.isIssued(inv)) {
      const bal = invBalance(inv);
      lines.push(`Invoice: ${inv.number}${invPaid(inv) ? ` · Paid ${money(invPaid(inv))}` : ""} · Balance ${bal > 0 ? money(bal) : "nil (paid in full)"}`);
    }
    if (j.notes) lines.push("", `Notes: ${j.notes}`);
    return lines.filter((x) => x !== null).join("\n") + "\n" + signature();
  }
  function invoiceShareText(inv) {
    const c = customer(inv.customerId);
    const t = invTotals(inv);
    const paid = invPaid(inv);
    const s = state.settings;
    const lines = [
      `*${inv.draft ? "Draft invoice" : "Invoice " + inv.number}*${inv.void ? " (VOID)" : ""}`,
      `Customer: ${c.name}`,
      `Date: ${fmtDate(inv.date)} · Due: ${fmtDate(inv.dueDate)}`,
      "",
      ...inv.lines.map((l) => `• ${l.desc} — ${money(C.lineAmount(l.qty, l.unitPrice))}`),
      "",
      `Subtotal: ${money(t.subtotal)}`,
      t.discount ? `Discount: −${money(t.discount)}` : null,
      `VAT ${t.rate}%: ${money(t.vat)}`,
      `*Total: ${money(t.total)}*`,
      paid ? `Paid: ${money(paid)}` : null,
      !inv.void && !inv.draft ? `*Balance due: ${money(t.total - paid)}*` : null,
      s.iban ? `\nBank transfer: ${s.bankName} · IBAN ${s.iban}\nPlease quote ${inv.number || "the invoice number"}.` : null,
    ];
    return lines.filter((x) => x !== null).join("\n") + "\n" + signature();
  }
  function customerShareText(c) {
    const t = todayIso();
    const jobs = state.jobs.filter((j) => j.customerId === c.id);
    const open = jobs.filter((j) => C.OPEN_JOB_STATUSES.includes(j.status) || j.status === "Quote");
    const invs = state.invoices.filter((i) => i.customerId === c.id && C.isIssued(i) && invBalance(i) > 0).sort((a, b) => a.date.localeCompare(b.date));
    const total = invs.reduce((s, i) => s + invBalance(i), 0);
    const lines = [`*Account summary — ${c.name}*`, `As of ${fmtDate(t)}`, ""];
    lines.push(open.length ? "*Orders in progress*" : "No orders in progress.");
    open.forEach((j) => {
      const p = C.jobProgress(j.status);
      lines.push(`• ${j.jobNo} ${j.title} — ${j.status} (${p.pct}%)${j.dueDate ? ", due " + fmtDate(j.dueDate) : ""}`);
    });
    lines.push("");
    if (invs.length) {
      lines.push("*Unpaid invoices*");
      invs.forEach((i) => lines.push(`• ${i.number} dated ${fmtDate(i.date)} — ${money(invBalance(i))}${i.dueDate < t ? " (overdue)" : ", due " + fmtDate(i.dueDate)}`));
      lines.push(`*Total balance due: ${money(total)}*`);
      if (state.settings.iban) lines.push("", `Bank transfer: ${state.settings.bankName} · IBAN ${state.settings.iban}`);
    } else lines.push("No balance due. Thank you!");
    return lines.join("\n") + "\n" + signature();
  }

  let shareBack = null;
  function shareModal(title, text, phone, back) {
    shareBack = back || null;
    const num = C.waPhone(phone);
    const href = (txt) => `https://wa.me/${num}?text=${encodeURIComponent(txt)}`;
    openModal({
      title: `Share on WhatsApp — ${title}`,
      body: `<label class="field"><span>Message (you can edit it before sending)</span>
          <textarea id="share-text" rows="16" class="share-text">${esc(text)}</textarea></label>
        <p class="hint">${num ? `Open WhatsApp starts a chat with <span class="mono">+${num}</span>.` : "No phone number is saved for this customer, so WhatsApp will ask you to pick a contact."} Internal cost and margin figures are never included.</p>`,
      foot: `${back ? `<button type="button" class="btn" data-act="share-back">Back</button>` : ""}<span class="spacer"></span>
        <button type="button" class="btn" data-act="share-copy">Copy text</button>
        <a class="btn primary" id="share-wa" href="${esc(href(text))}" target="_blank" rel="noopener">Open WhatsApp</a>`,
      onOpen: (body) => {
        const ta = $("#share-text", body);
        body.oninput = () => { $("#share-wa").href = href(ta.value); };
      },
    });
  }
  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (e) {
      const ta = $("#share-text") || Object.assign(document.createElement("textarea"), { value: text });
      if (!ta.isConnected) document.body.appendChild(ta);
      ta.focus();
      ta.select();
      let ok = false;
      try { ok = document.execCommand("copy"); } catch (_) { ok = false; }
      if (ta.id !== "share-text") ta.remove();
      return ok;
    }
  }

  /* ================= demo data ================= */
  function demoState() {
    const s = emptyState();
    s.demo = true;
    s.settings = {
      ...DEFAULT_SETTINGS,
      companyName: "Al Waha Printing Press (Sample)",
      companyNameAr: "مطبعة الواحة",
      vatNumber: "310123456700003",
      crNumber: "4030123456",
      address: "Al Andalus District, Jeddah 23326, Saudi Arabia",
      phone: "+966 12 000 0000",
      email: "accounts@example.sa",
      bankName: "Sample Bank",
      iban: "SA00 0000 0000 0000 0000 0000",
    };
    const t = todayIso();
    const cust = [
      { name: "Red Sea Dental Clinics", vatNumber: "300987654300003", phone: "+966 55 000 0001", email: "admin@example.sa" },
      { name: "Hijaz Real Estate", vatNumber: "311222333400003", phone: "+966 55 000 0002", email: "" },
      { name: "Café Balad", vatNumber: "", phone: "+966 55 000 0003", email: "" },
      { name: "Obhur Events Co.", vatNumber: "302555666700003", phone: "+966 55 000 0004", email: "" },
    ].map((c) => ({ id: uid(), address: "Jeddah", ...c }));
    s.customers = cust;
    const mk = (ci, title, product, qty, spec, price, cost, status, dueOffset, created) => {
      s.seq.job += 1;
      return {
        id: uid(), jobNo: `J-${pad(s.seq.job)}`, customerId: cust[ci].id, title, product, qty,
        size: spec[0], material: spec[1], colors: spec[2], finishing: spec[3],
        price, cost, status, dueDate: addDays(t, dueOffset), createdAt: addDays(t, created), notes: "",
      };
    };
    s.jobs = [
      mk(0, "Appointment cards – 5 branches", "Business cards", 5000, ["85×55 mm", "350 gsm art card", "4/4", "Matt lamination"], 1250, 610, "Delivered", -40, -55),
      mk(1, "Project brochure – Obhur towers", "Brochures", 2000, ["A4 → A5 fold", "170 gsm gloss", "4/4", "Half fold"], 4800, 2900, "Delivered", -75, -95),
      mk(2, "Menu cards + table tents", "Menus", 300, ["A4", "300 gsm + synthetic", "4/4", "Lamination"], 1650, 720, "Delivered", -12, -25),
      mk(3, "Event roll-ups ×6", "Banners", 6, ["85×200 cm", "Banner PVC", "4/0", "Roll-up stand"], 2100, 1260, "Delivered", -8, -18),
      mk(1, "Site hoarding banners", "Banners", 12, ["3×2 m", "Flex 440 g", "4/0", "Eyelets"], 6200, 3700, "Printing", 3, -6),
      mk(0, "Patient forms – NCR 2-part", "Forms", 10000, ["A5", "NCR 2-part", "1/0", "Padding 50s"], 2400, 1350, "Finishing", 1, -9),
      mk(2, "Coffee bag stickers", "Stickers", 4000, ["Ø 60 mm", "Vinyl", "4/0", "Die-cut"], 980, 430, "Prepress", 6, -3),
      mk(3, "VIP invitations – foil", "Invitations", 400, ["DL", "Curious Metallics 300 g", "2/0 + foil", "Gold foil, envelopes"], 3600, 1900, "Approved", 12, -1),
      mk(1, "Quote: annual report 60pp", "Books", 500, ["A4 portrait", "130 g silk + 300 g cover", "4/4", "Perfect bound"], 18500, 11200, "Quote", 30, 0),
    ];
    s.jobs.forEach((j) => {
      const idx = C.STAGES.indexOf(j.status);
      const end = j.status === "Delivered" ? j.dueDate : t;
      const span = Math.max(C.daysBetween(j.createdAt, end), idx);
      j.history = [{ at: j.createdAt + "T09:00:00", text: "Order created — Quote" }];
      for (let k = 1; k <= idx; k++) {
        j.history.push({ at: addDays(j.createdAt, Math.round((span * k) / idx)) + `T${10 + k}:00:00`, text: `Status changed: ${C.STAGES[k - 1]} → ${C.STAGES[k]}` });
      }
    });
    const lineFor = (j) => ({ desc: `${j.title} — ${j.qty.toLocaleString("en-US")} pcs, ${j.size}, ${j.material}, ${j.colors}, ${j.finishing}`, qty: 1, unitPrice: j.price });
    const issue = (jobIdx, dateOffset, extra = {}) => {
      const j = s.jobs[jobIdx];
      s.seq.invoice += 1;
      const date = addDays(t, dateOffset);
      const inv = {
        id: uid(), number: `INV-${date.slice(0, 4)}-${pad(s.seq.invoice)}`, customerId: j.customerId,
        jobIds: [j.id], date, dueDate: addDays(date, 30), issuedAt: date + "T09:00:00Z",
        lines: [lineFor(j)], discount: 0, vatRate: 15, notes: "", draft: false, void: false, ...extra,
      };
      j.invoiceId = inv.id;
      s.invoices.push(inv);
      return inv;
    };
    const i1 = issue(0, -40);
    const i2 = issue(1, -75, { discount: 200 });
    const i3 = issue(2, -12);
    const i4 = issue(3, -8);
    const pay = (inv, amt, off, method, ref) => {
      s.seq.receipt += 1;
      s.payments.push({ id: uid(), receiptNo: `RCT-${pad(s.seq.receipt)}`, invoiceId: inv.id, date: addDays(t, off), amount: amt, method, reference: ref });
    };
    pay(i1, 1437.5, -20, "Bank transfer", "SARIE 88123");
    pay(i2, 3000, -30, "Cheque", "CHQ 004512");
    pay(i3, 1000, -5, "Card / mada", "POS 7781");
    void i4;
    return s;
  }

  /* ================= ui primitives ================= */
  const modal = $("#modal");
  let modalSubmit = null;

  function openModal({ title, body, foot, onSubmit, wide = false, onOpen }) {
    $("#modal-title").textContent = title;
    const mb = $("#modal-body");
    mb.oninput = mb.onclick = null; // drop handlers left by the previous form
    mb.innerHTML = body;
    $("#modal-foot").innerHTML =
      foot ?? `<span class="spacer"></span><button type="button" class="btn" data-close>Cancel</button><button type="submit" class="btn primary">Save</button>`;
    modal.classList.toggle("wide", wide);
    modalSubmit = onSubmit || null;
    if (!modal.open) modal.showModal();
    if (onOpen) onOpen($("#modal-body"));
    const first = $("#modal-body input, #modal-body select, #modal-body textarea");
    if (first) first.focus();
  }
  function closeModal() {
    if (modal.open) modal.close();
    modalSubmit = null;
  }
  modal.addEventListener("click", (e) => {
    if (e.target.closest("[data-close]")) closeModal();
  });
  $("#modal-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const action = e.submitter?.value || "save";
    if (modalSubmit) {
      const keep = modalSubmit(new FormData(e.target), action, e.target);
      if (keep !== false) closeModal();
    } else closeModal();
  });

  function confirmBox(title, message, label, onYes) {
    openModal({
      title,
      body: `<p>${message}</p>`,
      foot: `<span class="spacer"></span><button type="button" class="btn" data-close>Cancel</button><button type="submit" class="btn primary">${esc(label)}</button>`,
      onSubmit: () => { onYes(); },
    });
  }

  let toastTimer;
  function toast(msg) {
    const t = $("#toast");
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => (t.hidden = true), 2600);
  }

  function fieldError(form, name, msg) {
    let el = form.elements[name];
    if (el && !el.classList) el = el[0]; // several inputs share the name (invoice lines)
    if (el) {
      el.classList.add("invalid");
      el.focus();
    }
    toast(msg);
    return false;
  }

  function pill(status) {
    const map = {
      Paid: "good", Delivered: "good", Ready: "good",
      Partial: "warn", Unpaid: "info", Draft: "", Void: "", Quote: "", Cancelled: "",
      Overdue: "bad", Late: "bad",
      Approved: "info", Prepress: "info", Printing: "info", Finishing: "info",
    };
    return `<span class="pill ${map[status] ?? ""}">${esc(status)}</span>`;
  }

  function download(filename, text, type = "text/csv") {
    const blob = new Blob([text], { type: type + ";charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 500);
  }
  function toCsv(rows) {
    return "﻿" + rows.map((r) => r.map((v) => {
      const s = String(v ?? "");
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    }).join(",")).join("\r\n");
  }

  const customerOptions = (sel) =>
    state.customers
      .slice()
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((c) => `<option value="${c.id}" ${c.id === sel ? "selected" : ""}>${esc(c.name)}</option>`)
      .join("");

  /* ================= banner ================= */
  function renderBanner() {
    const b = $("#banner");
    if (!storageOk) {
      b.hidden = false;
      b.innerHTML = `<span>This browser is blocking storage, so changes will be lost when you close the page. Use <b>Settings → Export backup</b> to keep a copy.</span>`;
    } else if (state.demo) {
      b.hidden = false;
      b.innerHTML = `<span>You are looking at <b>sample data</b>. Enter your company details in Settings, then clear the sample records to start.</span><a class="btn sm" href="#settings">Go to Settings</a>`;
    } else b.hidden = true;
    $("#brand-name").textContent = state.settings.companyName || "PrintOps";
  }

  /* ================= router ================= */
  const routes = {};
  let current = "dashboard";
  let routeParam = "";
  const viewState = { jobFilter: "open", jobQuery: "", invFilter: "all", invQuery: "", repFrom: "", repTo: "" };

  function route() {
    const [r, param] = (location.hash || "#dashboard").slice(1).split("/");
    current = routes[r] ? r : "dashboard";
    routeParam = decodeURIComponent(param || "");
    const navKey = current === "customer" ? "customers" : current;
    $$(".nav a").forEach((a) => a.classList.toggle("active", a.dataset.route === navKey));
    if (modal.open) closeModal();
    render();
  }
  function render() {
    renderBanner();
    $("#view").innerHTML = routes[current]();
    window.scrollTo(0, 0);
  }
  window.addEventListener("hashchange", route);

  /* ================= dashboard ================= */
  routes.dashboard = () => {
    const t = todayIso();
    const monthStart = t.slice(0, 8) + "01";
    const open = state.jobs.filter((j) => C.OPEN_JOB_STATUSES.includes(j.status));
    const lateJobs = open.filter((j) => j.dueDate && j.dueDate < t);
    const dueSoon = open.filter((j) => j.dueDate && j.dueDate >= t && j.dueDate <= addDays(t, 7));
    const ag = C.aging(state.invoices, state.payments, t);
    const overdue = ag.d1_30 + ag.d31_60 + ag.d61_90 + ag.d90p;
    const issued = state.invoices.filter(C.isIssued);
    const invoicedMonth = issued.filter((i) => i.date >= monthStart && i.date <= t).reduce((s, i) => s + invTotals(i).total, 0);
    const collectedMonth = state.payments.filter((p) => p.date >= monthStart && p.date <= t).reduce((s, p) => s + C.toHalalas(p.amount), 0);
    const uninvoiced = state.jobs.filter((j) => ["Ready", "Delivered"].includes(j.status) && !j.invoiceId);

    const stageColors = { Quote: "var(--ink-3)", Approved: "var(--cyan)", Prepress: "var(--magenta)", Printing: "var(--yellow)", Finishing: "var(--key)", Ready: "var(--good)" };
    const stages = ["Quote", ...C.OPEN_JOB_STATUSES]
      .map((st) => `<a class="stage" href="#jobs" data-jobfilter="${st}" style="--c:${stageColors[st]}"><span class="n">${state.jobs.filter((j) => j.status === st).length}</span><span class="t">${st}</span></a>`)
      .join("");

    const upcoming = open
      .slice()
      .sort((a, b) => (a.dueDate || "9").localeCompare(b.dueDate || "9"))
      .slice(0, 8)
      .map((j) => `<tr class="${j.dueDate < t ? "late" : ""}">
        <td class="title-cell"><button class="link-btn" data-act="view-job" data-id="${j.id}"><span class="mono">${esc(j.jobNo)}</span> ${esc(j.title)}</button><small>${esc(customer(j.customerId).name)}</small></td>
        <td class="prog-cell">${progressBar(j, true)} <span class="small">${esc(j.status)}</span></td>
        <td class="num">${fmtDate(j.dueDate)}${j.dueDate < t ? `<br>${pill("Late")}` : ""}</td>
        <td>${nextStepButton(j, "btn sm")}</td></tr>`)
      .join("");

    const recentPays = state.payments
      .slice()
      .sort((a, b) => b.date.localeCompare(a.date))
      .slice(0, 6)
      .map((p) => {
        const inv = byId(state.invoices, p.invoiceId);
        return `<tr><td>${fmtDate(p.date)}</td><td>${esc(inv ? customer(inv.customerId).name : "—")}<br><span class="muted small mono">${esc(inv?.number || "")}</span></td><td>${esc(p.method)}</td><td class="num">${sar(C.toHalalas(p.amount))}</td></tr>`;
      })
      .join("");

    return `
      <div class="page-head">
        <div><h1>Dashboard</h1><p>${fmtDate(t)} · amounts in SAR</p></div>
        <div class="actions">
          <button class="btn" data-act="new-job">New job</button>
          <button class="btn" data-act="new-invoice">New invoice</button>
          <button class="btn primary" data-act="new-payment">Record payment</button>
        </div>
      </div>
      <div class="kpis">
        <div class="kpi"><span class="label">Jobs in production</span><span class="value">${open.length}</span><span class="sub">${dueSoon.length} due in the next 7 days</span></div>
        <div class="kpi ${lateJobs.length ? "alert" : ""}"><span class="label">Jobs past due date</span><span class="value">${lateJobs.length}</span><span class="sub">${lateJobs.length ? "Check the list below" : "All on schedule"}</span></div>
        <div class="kpi"><span class="label">Receivables</span><span class="value">${sar(ag.total)}</span><span class="sub">Open balance on issued invoices</span></div>
        <div class="kpi ${overdue ? "alert" : ""}"><span class="label">Overdue</span><span class="value">${sar(overdue)}</span><span class="sub">Past the invoice due date</span></div>
        <div class="kpi"><span class="label">Invoiced this month</span><span class="value">${sar(invoicedMonth)}</span><span class="sub">Incl. VAT</span></div>
        <div class="kpi"><span class="label">Collected this month</span><span class="value">${sar(collectedMonth)}</span><span class="sub">${state.payments.filter((p) => p.date >= monthStart).length} payments</span></div>
      </div>

      <h2 style="margin:22px 0 10px">Production pipeline</h2>
      <div class="pipeline">${stages}</div>
      ${uninvoiced.length ? `<p class="hint" style="margin-top:8px">${uninvoiced.length} finished job${uninvoiced.length > 1 ? "s are" : " is"} not invoiced yet. <a href="#jobs" data-jobfilter="uninvoiced">Review</a></p>` : ""}

      <div class="cols">
        <section class="panel">
          <div class="panel-head"><h2>Orders to complete</h2><a href="#jobs" class="small">All jobs</a></div>
          <div class="table-wrap">${upcoming ? `<table><tbody>${upcoming}</tbody></table>` : `<div class="empty">No jobs in production.</div>`}</div>
        </section>
        <section class="panel">
          <div class="panel-head"><h2>Receivables aging</h2><a href="#reports" class="small">Report</a></div>
          <div class="panel-body">
            <div class="aging">
              <div><span class="l">Current</span><br><span class="v">${sar(ag.current)}</span></div>
              <div class="${ag.d1_30 ? "hot" : ""}"><span class="l">1–30</span><br><span class="v">${sar(ag.d1_30)}</span></div>
              <div class="${ag.d31_60 ? "hot" : ""}"><span class="l">31–60</span><br><span class="v">${sar(ag.d31_60)}</span></div>
              <div class="${ag.d61_90 ? "hot" : ""}"><span class="l">61–90</span><br><span class="v">${sar(ag.d61_90)}</span></div>
              <div class="${ag.d90p ? "hot" : ""}"><span class="l">90+</span><br><span class="v">${sar(ag.d90p)}</span></div>
            </div>
            <h3 style="margin:16px 0 6px">Recent payments</h3>
            ${recentPays ? `<div class="table-wrap"><table><tbody>${recentPays}</tbody></table></div>` : `<div class="empty">No payments recorded.</div>`}
          </div>
        </section>
      </div>`;
  };

  /* ================= jobs ================= */
  routes.jobs = () => {
    const t = todayIso();
    const f = viewState.jobFilter;
    const q = viewState.jobQuery.toLowerCase();
    let list = state.jobs.slice();
    if (f === "open") list = list.filter((j) => C.OPEN_JOB_STATUSES.includes(j.status));
    else if (f === "uninvoiced") list = list.filter((j) => ["Ready", "Delivered"].includes(j.status) && !j.invoiceId);
    else if (f !== "all") list = list.filter((j) => j.status === f);
    if (q) list = list.filter((j) => [j.jobNo, j.title, j.product, customer(j.customerId).name].join(" ").toLowerCase().includes(q));
    list.sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || "") || b.jobNo.localeCompare(a.jobNo));

    const count = (st) => state.jobs.filter((j) => j.status === st).length;
    const chips = [
      ["open", "In production", state.jobs.filter((j) => C.OPEN_JOB_STATUSES.includes(j.status)).length],
      ["all", "All", state.jobs.length],
      ...C.JOB_STATUSES.map((s) => [s, s, count(s)]),
      ["uninvoiced", "Not invoiced", state.jobs.filter((j) => ["Ready", "Delivered"].includes(j.status) && !j.invoiceId).length],
    ].map(([k, l, n]) => `<button class="chip ${f === k ? "on" : ""}" data-jobfilter="${k}">${l}<b>${n}</b></button>`).join("");

    const rows = list.map((j) => {
      const inv = j.invoiceId && byId(state.invoices, j.invoiceId);
      const late = C.OPEN_JOB_STATUSES.includes(j.status) && j.dueDate && j.dueDate < t;
      const margin = C.toHalalas(j.price) - C.toHalalas(j.cost);
      return `<tr class="${late ? "late" : ""}">
        <td class="mono"><button class="link-btn mono" data-act="view-job" data-id="${j.id}">${esc(j.jobNo)}</button></td>
        <td class="title-cell"><button class="link-btn title-link" data-act="view-job" data-id="${j.id}">${esc(j.title)}</button><small><a href="#customer/${j.customerId}">${esc(customer(j.customerId).name)}</a> · ${esc(j.product)}</small></td>
        <td class="small">${esc([j.size, j.material, j.colors, j.finishing].filter(Boolean).join(" · "))}</td>
        <td class="num">${Number(j.qty || 0).toLocaleString("en-US")}</td>
        <td class="num">${fmtDate(j.dueDate)}${late ? `<br>${pill("Late")}` : ""}</td>
        <td><select class="status-select" data-jobstatus="${j.id}" aria-label="Status of ${esc(j.jobNo)}">${C.JOB_STATUSES.map((s) => `<option ${s === j.status ? "selected" : ""}>${s}</option>`).join("")}</select><div class="prog-cell">${progressBar(j, true)}</div></td>
        <td class="num">${sar(C.toHalalas(j.price))}<br><span class="small ${margin < 0 ? "error" : "muted"}">margin ${sar(margin)}</span></td>
        <td>${inv ? `<button class="link-btn mono" data-act="view-invoice" data-id="${inv.id}">${esc(inv.number || "Draft")}</button>` : j.status === "Quote" || j.status === "Cancelled" ? `<span class="muted">—</span>` : `<button class="btn sm" data-act="invoice-job" data-id="${j.id}">Invoice</button>`}</td>
        <td><div class="row-actions"><button class="btn sm" data-act="edit-job" data-id="${j.id}">Edit</button><button class="btn sm danger" data-act="del-job" data-id="${j.id}" aria-label="Delete ${esc(j.jobNo)}">Delete</button></div></td>
      </tr>`;
    }).join("");

    return `
      <div class="page-head">
        <div><h1>Jobs</h1><p>Every print order from quote to delivery. Click an order to see its details and history.</p></div>
        <div class="actions"><button class="btn primary" data-act="new-job">New job</button></div>
      </div>
      <div class="toolbar"><div class="chips">${chips}</div></div>
      <div class="toolbar"><input type="search" id="job-search" placeholder="Search job, customer, product" value="${esc(viewState.jobQuery)}"></div>
      <div class="table-wrap">${rows ? `<table>
        <thead><tr><th>Job</th><th>Description</th><th>Spec</th><th class="num">Qty</th><th class="num">Due</th><th>Status</th><th class="num">Price (excl. VAT)</th><th>Invoice</th><th></th></tr></thead>
        <tbody>${rows}</tbody></table>` : `<div class="empty">No jobs match this filter.</div>`}</div>`;
  };

  const PRODUCTS = ["Business cards", "Flyers", "Brochures", "Posters", "Banners", "Stickers", "Menus", "Forms", "Invitations", "Books", "Packaging", "Letterheads", "Envelopes", "Other"];

  function jobForm(job, preset = {}) {
    const j = job || { status: "Approved", dueDate: addDays(todayIso(), 7), qty: 1000, colors: "4/4", ...preset };
    if (!state.customers.length) {
      toast("Add a customer first.");
      return customerForm(null, () => jobForm(job, preset));
    }
    openModal({
      title: job ? `Edit job ${job.jobNo}` : "New job",
      wide: true,
      body: `<div class="grid-form three">
        <label class="field full"><span>Customer</span><select name="customerId" required>${customerOptions(j.customerId)}</select></label>
        <label class="field full"><span>Job title</span><input name="title" required value="${esc(j.title)}" placeholder="e.g. Business cards for sales team"></label>
        <label class="field"><span>Product</span><select name="product">${PRODUCTS.map((p) => `<option ${p === j.product ? "selected" : ""}>${p}</option>`).join("")}</select></label>
        <label class="field"><span>Quantity</span><input name="qty" type="number" min="0" step="1" value="${esc(j.qty)}"></label>
        <label class="field"><span>Status</span><select name="status">${C.JOB_STATUSES.map((s) => `<option ${s === j.status ? "selected" : ""}>${s}</option>`).join("")}</select></label>
        <label class="field"><span>Size</span><input name="size" value="${esc(j.size)}" placeholder="A4, 85×55 mm"></label>
        <label class="field"><span>Paper / material</span><input name="material" value="${esc(j.material)}" placeholder="350 gsm art card"></label>
        <label class="field"><span>Colours</span><input name="colors" value="${esc(j.colors)}" placeholder="4/4, 4/0, 1/0"></label>
        <label class="field"><span>Finishing</span><input name="finishing" value="${esc(j.finishing)}" placeholder="Lamination, die-cut, binding"></label>
        <label class="field"><span>Due date</span><input name="dueDate" type="date" value="${esc(j.dueDate)}"></label>
        <span></span>
        <label class="field"><span>Selling price, SAR excl. VAT</span><input name="price" type="number" min="0" step="0.01" value="${esc(j.price ?? "")}" required></label>
        <label class="field"><span>Estimated cost, SAR</span><input name="cost" type="number" min="0" step="0.01" value="${esc(j.cost ?? "")}"></label>
        <span class="hint" style="align-self:end">Cost covers paper, plates, ink, outsourced finishing. Used for margin only.</span>
        <label class="field full"><span>Notes</span><textarea name="notes">${esc(j.notes)}</textarea></label>
      </div>`,
      onSubmit: (fd, _a, form) => {
        const d = Object.fromEntries(fd);
        if (!d.title.trim()) return fieldError(form, "title", "Give the job a title.");
        if (d.price === "" || Number(d.price) < 0) return fieldError(form, "price", "Enter the selling price (0 or more).");
        const data = {
          ...d, title: d.title.trim(), qty: Number(d.qty) || 0,
          price: C.toSar(C.toHalalas(d.price)), cost: C.toSar(C.toHalalas(d.cost || 0)),
        };
        if (job) {
          if (!job.history?.length) job.history = jobHistory(job).slice();
          const { status, ...rest } = data;
          const changed = Object.keys(rest).some((k) => String(job[k] ?? "") !== String(rest[k] ?? ""));
          Object.assign(job, rest);
          if (changed) logJob(job, "Order details edited");
          setStatus(job, status);
        } else {
          state.seq.job += 1;
          const nj = { id: uid(), jobNo: `J-${pad(state.seq.job)}`, createdAt: todayIso(), history: [], ...data };
          logJob(nj, `Order created — ${nj.status}`);
          state.jobs.push(nj);
        }
        save();
        render();
        toast(job ? "Job updated" : "Job created");
      },
    });
  }

  /* ================= customers ================= */
  function customerForm(c, after) {
    const x = c || {};
    openModal({
      title: c ? "Edit customer" : "New customer",
      body: `<div class="grid-form">
        <label class="field full"><span>Name</span><input name="name" required value="${esc(x.name)}"></label>
        <label class="field"><span>VAT number</span><input name="vatNumber" inputmode="numeric" maxlength="15" value="${esc(x.vatNumber)}" placeholder="15 digits, optional"></label>
        <label class="field"><span>CR number</span><input name="crNumber" value="${esc(x.crNumber)}"></label>
        <label class="field"><span>Phone</span><input name="phone" value="${esc(x.phone)}"></label>
        <label class="field"><span>Email</span><input name="email" type="email" value="${esc(x.email)}"></label>
        <label class="field full"><span>Address</span><input name="address" value="${esc(x.address)}"></label>
        <p class="hint full">Customers with a VAT number receive a standard <b>Tax Invoice</b>; others receive a <b>Simplified Tax Invoice</b>.</p>
      </div>`,
      onSubmit: (fd, _a, form) => {
        const d = Object.fromEntries(fd);
        d.name = d.name.trim();
        d.vatNumber = d.vatNumber.replace(/\s/g, "");
        if (!d.name) return fieldError(form, "name", "Enter the customer name.");
        if (d.vatNumber && !C.isValidVatNumber(d.vatNumber)) return fieldError(form, "vatNumber", "A Saudi VAT number has 15 digits and starts and ends with 3.");
        if (c) Object.assign(c, d);
        else state.customers.push({ id: uid(), ...d });
        save();
        toast(c ? "Customer updated" : "Customer added");
        if (after) { closeModal(); after(); return false; }
        render();
      },
    });
  }

  routes.customers = () => {
    const rows = state.customers
      .slice()
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((c) => {
        const invs = state.invoices.filter((i) => i.customerId === c.id && C.isIssued(i));
        const billed = invs.reduce((s, i) => s + invTotals(i).total, 0);
        const paid = invs.reduce((s, i) => s + invPaid(i), 0);
        const jobs = state.jobs.filter((j) => j.customerId === c.id).length;
        return `<tr>
          <td class="title-cell"><a class="title-link" href="#customer/${c.id}">${esc(c.name)}</a><small>${esc([c.phone, c.email].filter(Boolean).join(" · "))}</small></td>
          <td class="mono small">${esc(c.vatNumber || "—")}</td>
          <td class="num">${jobs}</td>
          <td class="num">${sar(billed)}</td><td class="num">${sar(paid)}</td>
          <td class="num"><b>${sar(billed - paid)}</b></td>
          <td><div class="row-actions"><button class="btn sm" data-act="statement" data-id="${c.id}">Statement</button><button class="btn sm" data-act="edit-customer" data-id="${c.id}">Edit</button><button class="btn sm danger" data-act="del-customer" data-id="${c.id}">Delete</button></div></td>
        </tr>`;
      }).join("");
    return `
      <div class="page-head"><div><h1>Customers</h1><p>Click a customer to see all their orders and activity. Balances include VAT and cover issued invoices only.</p></div>
      <div class="actions"><button class="btn primary" data-act="new-customer">New customer</button></div></div>
      <div class="table-wrap">${rows ? `<table><thead><tr><th>Customer</th><th>VAT no.</th><th class="num">Jobs</th><th class="num">Invoiced</th><th class="num">Received</th><th class="num">Balance</th><th></th></tr></thead><tbody>${rows}</tbody></table>` : `<div class="empty">No customers yet. Add your first customer to start logging jobs.</div>`}</div>`;
  };

  function viewJob(j) {
    const t = todayIso();
    const c = customer(j.customerId);
    const p = C.jobProgress(j.status);
    const late = C.OPEN_JOB_STATUSES.includes(j.status) && j.dueDate && j.dueDate < t;
    const inv = j.invoiceId && byId(state.invoices, j.invoiceId);
    const issued = inv && C.isIssued(inv);
    const tt = issued ? invTotals(inv) : C.invoiceTotals({ lines: [{ qty: 1, unitPrice: j.price }], vatRate: state.settings.vatRate });
    const margin = C.toHalalas(j.price) - C.toHalalas(j.cost);
    const spec = [["Product", j.product], ["Quantity", Number(j.qty || 0).toLocaleString("en-US")], ["Size", j.size], ["Paper / material", j.material], ["Colours", j.colors], ["Finishing", j.finishing], ["Ordered", fmtDate(j.createdAt)], ["Due date", fmtDate(j.dueDate)]];
    openModal({
      title: `${j.jobNo} · ${j.title}`,
      wide: true,
      body: `
        <div class="detail-head">
          <div><span class="muted small">Customer</span><br><a class="title-link" href="#customer/${c.id}">${esc(c.name)}</a>${c.phone ? `<br><span class="small mono">${esc(c.phone)}</span>` : ""}</div>
          <div><span class="muted small">Status</span><br>${pill(j.status)} ${late ? pill("Late") : ""}</div>
          <div><span class="muted small">Due</span><br><b>${fmtDate(j.dueDate)}</b></div>
          <div class="grow"><span class="muted small">Progress to completion</span><br>${progressBar(j)}</div>
        </div>
        ${stepper(j)}
        <div class="detail-cols">
          <section><h3>Order details</h3><dl class="kv">${spec.map(([k, v]) => `<dt>${k}</dt><dd>${esc(v || "—")}</dd>`).join("")}</dl>
            ${j.notes ? `<h3 style="margin-top:12px">Notes</h3><p class="pre">${esc(j.notes)}</p>` : ""}</section>
          <section><h3>Amount</h3><dl class="kv">
              <dt>Price excl. VAT</dt><dd class="num">${sar(tt.taxable)}</dd>
              <dt>VAT ${tt.rate}%</dt><dd class="num">${sar(tt.vat)}</dd>
              <dt><b>Total incl. VAT</b></dt><dd class="num"><b>${sar(tt.total)}</b></dd>
            </dl>
            <h3 style="margin-top:12px">Invoice & payment</h3>
            ${inv ? `<dl class="kv"><dt>Invoice</dt><dd><button type="button" class="link-btn mono" data-act="view-invoice" data-id="${inv.id}">${esc(inv.number || "Draft")}</button> ${pill(invStatus(inv))}</dd>
              ${issued ? `<dt>Paid</dt><dd class="num">${sar(invPaid(inv))}</dd><dt>Balance</dt><dd class="num"><b>${sar(invBalance(inv))}</b></dd>` : ""}</dl>`
              : `<p class="muted">Not invoiced yet.</p>`}
            <div class="internal"><span class="small muted">Internal only, never shared</span>
              <dl class="kv"><dt>Estimated cost</dt><dd class="num">${sar(C.toHalalas(j.cost))}</dd><dt>Margin</dt><dd class="num ${margin < 0 ? "error" : ""}">${sar(margin)}${C.toHalalas(j.price) ? ` (${((100 * margin) / C.toHalalas(j.price)).toFixed(1)}%)` : ""}</dd></dl></div>
          </section>
        </div>
        <h3 style="margin:16px 0 8px">Activity</h3>
        ${timeline(activityFor([j], jobInvoices(j)))}`,
      foot: `<button type="button" class="btn" data-act="share-job" data-id="${j.id}">Share on WhatsApp</button>
        <button type="button" class="btn" data-act="edit-job" data-id="${j.id}">Edit</button>
        <span class="spacer"></span>
        ${!inv && !p.cancelled && j.status !== "Quote" ? `<button type="button" class="btn" data-act="invoice-job" data-id="${j.id}">Create invoice</button>` : ""}
        ${nextStepButton(j) || `<button type="button" class="btn primary" data-close>Close</button>`}`,
    });
  }

  routes.customer = () => {
    const c = byId(state.customers, routeParam);
    if (!c) return `<div class="page-head"><div><h1>Customer not found</h1><p><a href="#customers">Back to customers</a></p></div></div>`;
    const t = todayIso();
    const jobs = state.jobs.filter((j) => j.customerId === c.id).sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || "") || b.jobNo.localeCompare(a.jobNo));
    const invs = state.invoices.filter((i) => i.customerId === c.id).sort((a, b) => (b.date || "").localeCompare(a.date || ""));
    const issued = invs.filter(C.isIssued);
    const billed = issued.reduce((s, i) => s + invTotals(i).total, 0);
    const paid = issued.reduce((s, i) => s + invPaid(i), 0);
    const overdue = issued.filter((i) => invStatus(i) === "Overdue").reduce((s, i) => s + invBalance(i), 0);
    const inProd = jobs.filter((j) => C.OPEN_JOB_STATUSES.includes(j.status)).length;
    const done = jobs.filter((j) => j.status === "Delivered").length;
    const orderRows = jobs.map((j) => {
      const inv = j.invoiceId && byId(state.invoices, j.invoiceId);
      const late = C.OPEN_JOB_STATUSES.includes(j.status) && j.dueDate && j.dueDate < t;
      return `<tr class="${late ? "late" : ""}">
        <td class="mono"><button class="link-btn mono" data-act="view-job" data-id="${j.id}">${esc(j.jobNo)}</button></td>
        <td class="title-cell"><button class="link-btn title-link" data-act="view-job" data-id="${j.id}">${esc(j.title)}</button><small>${esc(j.product)} · ${Number(j.qty || 0).toLocaleString("en-US")} pcs</small></td>
        <td class="num">${fmtDate(j.createdAt)}</td><td class="num">${fmtDate(j.dueDate)}</td>
        <td class="prog-cell">${pill(j.status)}${progressBar(j, true)}</td>
        <td class="num">${sar(C.toHalalas(j.price))}</td>
        <td>${inv ? `<button class="link-btn mono" data-act="view-invoice" data-id="${inv.id}">${esc(inv.number || "Draft")}</button><br>${pill(invStatus(inv))}` : `<span class="muted">—</span>`}</td></tr>`;
    }).join("");
    const invRows = invs.map((i) => `<tr><td><button class="link-btn mono" data-act="view-invoice" data-id="${i.id}">${esc(i.number || "Draft")}</button></td><td class="num">${fmtDate(i.date)}</td><td class="num">${sar(invTotals(i).total)}</td><td class="num">${C.isIssued(i) ? sar(invBalance(i)) : "—"}</td><td>${pill(invStatus(i))}</td></tr>`).join("");
    return `
      <p style="margin:0 0 6px"><a href="#customers" class="small">← All customers</a></p>
      <div class="page-head">
        <div><h1>${esc(c.name)}</h1><p>${esc([c.vatNumber && "VAT " + c.vatNumber, c.phone, c.email, c.address].filter(Boolean).join(" · ") || "No contact details saved")}</p></div>
        <div class="actions">
          <button class="btn" data-act="share-customer" data-id="${c.id}">Share on WhatsApp</button>
          <button class="btn" data-act="statement" data-id="${c.id}">Statement</button>
          <button class="btn" data-act="edit-customer" data-id="${c.id}">Edit</button>
          <button class="btn" data-act="new-invoice" data-id="${c.id}">New invoice</button>
          <button class="btn primary" data-act="new-job" data-id="${c.id}">New order</button>
        </div>
      </div>
      <div class="kpis">
        <div class="kpi"><span class="label">Orders</span><span class="value">${jobs.length}</span><span class="sub">${done} completed</span></div>
        <div class="kpi"><span class="label">In production</span><span class="value">${inProd}</span><span class="sub">${jobs.filter((j) => j.status === "Quote").length} quote(s) pending</span></div>
        <div class="kpi"><span class="label">Invoiced</span><span class="value">${sar(billed)}</span><span class="sub">Incl. VAT</span></div>
        <div class="kpi"><span class="label">Received</span><span class="value">${sar(paid)}</span><span class="sub">${state.payments.filter((p) => issued.some((i) => i.id === p.invoiceId)).length} payments</span></div>
        <div class="kpi ${overdue ? "alert" : ""}"><span class="label">Balance due</span><span class="value">${sar(billed - paid)}</span><span class="sub">${overdue ? `${sar(overdue)} overdue` : "Nothing overdue"}</span></div>
      </div>
      <section class="panel" style="margin-top:16px">
        <div class="panel-head"><h2>All orders</h2><span class="small muted">${jobs.length} order${jobs.length === 1 ? "" : "s"}</span></div>
        <div class="table-wrap">${orderRows ? `<table><thead><tr><th>Job</th><th>Description</th><th class="num">Ordered</th><th class="num">Due</th><th>Progress</th><th class="num">Price excl. VAT</th><th>Invoice</th></tr></thead><tbody>${orderRows}</tbody></table>` : `<div class="empty">No orders yet. Click New order to add one.</div>`}</div>
      </section>
      <div class="cols">
        <section class="panel">
          <div class="panel-head"><h2>Activity</h2></div>
          <div class="panel-body">${timeline(activityFor(jobs, invs))}</div>
        </section>
        <section class="panel">
          <div class="panel-head"><h2>Invoices</h2></div>
          <div class="table-wrap">${invRows ? `<table><thead><tr><th>Number</th><th class="num">Date</th><th class="num">Total</th><th class="num">Balance</th><th>Status</th></tr></thead><tbody>${invRows}</tbody></table>` : `<div class="empty">No invoices yet.</div>`}</div>
        </section>
      </div>`;
  };

  function statement(c) {
    const invs = state.invoices.filter((i) => i.customerId === c.id && C.isIssued(i));
    const ids = new Set(invs.map((i) => i.id));
    const entries = [
      ...invs.map((i) => ({ date: i.date, ref: i.number, desc: "Invoice", dr: invTotals(i).total, cr: 0 })),
      ...state.payments.filter((p) => ids.has(p.invoiceId)).map((p) => ({ date: p.date, ref: p.receiptNo, desc: `Payment — ${p.method}${p.reference ? " (" + p.reference + ")" : ""} for ${byId(state.invoices, p.invoiceId).number}`, dr: 0, cr: C.toHalalas(p.amount) })),
    ].sort((a, b) => a.date.localeCompare(b.date) || b.dr - a.dr);
    let bal = 0, dr = 0, cr = 0;
    const rows = entries.map((e) => {
      bal += e.dr - e.cr; dr += e.dr; cr += e.cr;
      return `<tr><td>${fmtDate(e.date)}</td><td class="mono small">${esc(e.ref)}</td><td>${esc(e.desc)}</td><td class="num">${e.dr ? sar(e.dr) : ""}</td><td class="num">${e.cr ? sar(e.cr) : ""}</td><td class="num">${sar(bal)}</td></tr>`;
    }).join("");
    const openBal = invs.reduce((s, i) => s + invBalance(i), 0);
    const ok = openBal === bal;
    openModal({
      title: `Statement — ${c.name}`,
      wide: true,
      body: `<div class="table-wrap"><table><thead><tr><th>Date</th><th>Ref</th><th>Details</th><th class="num">Debit</th><th class="num">Credit</th><th class="num">Balance</th></tr></thead>
        <tbody>${rows || `<tr><td colspan="6" class="empty">No transactions.</td></tr>`}</tbody>
        <tfoot><tr><td colspan="3">Closing balance (SAR)</td><td class="num">${sar(dr)}</td><td class="num">${sar(cr)}</td><td class="num">${sar(bal)}</td></tr></tfoot></table></div>
        <p class="check ${ok ? "ok" : "fail"}" style="margin-top:10px">${ok ? "✓ Ties to open invoice balances" : "✗ Does not tie to open invoice balances"} (${sar(openBal)})</p>`,
      foot: `<button type="button" class="btn" data-act="csv-statement" data-id="${c.id}">Export CSV</button><span class="spacer"></span><button type="button" class="btn primary" data-close>Close</button>`,
    });
    statement.last = { c, entries };
  }

  /* ================= invoices ================= */
  routes.invoices = () => {
    const f = viewState.invFilter;
    const q = viewState.invQuery.toLowerCase();
    let list = state.invoices.slice();
    if (f !== "all") list = list.filter((i) => invStatus(i) === f);
    if (q) list = list.filter((i) => [i.number, customer(i.customerId).name].join(" ").toLowerCase().includes(q));
    list.sort((a, b) => (b.date || "").localeCompare(a.date || "") || (b.number || "").localeCompare(a.number || ""));
    const statuses = ["all", "Draft", "Unpaid", "Partial", "Overdue", "Paid", "Void"];
    const chips = statuses.map((s) => `<button class="chip ${f === s ? "on" : ""}" data-invfilter="${s}">${s === "all" ? "All" : s}<b>${s === "all" ? state.invoices.length : state.invoices.filter((i) => invStatus(i) === s).length}</b></button>`).join("");
    let sumT = 0, sumP = 0, sumB = 0;
    const rows = list.map((i) => {
      const st = invStatus(i);
      const tt = invTotals(i).total;
      const pd = invPaid(i);
      const counts = C.isIssued(i);
      if (counts) { sumT += tt; sumP += pd; sumB += tt - pd; }
      return `<tr>
        <td><button class="link-btn mono" data-act="view-invoice" data-id="${i.id}">${esc(i.number || "Draft")}</button></td>
        <td>${esc(customer(i.customerId).name)}</td>
        <td class="num">${fmtDate(i.date)}</td><td class="num">${fmtDate(i.dueDate)}</td>
        <td class="num">${sar(tt)}</td><td class="num">${sar(pd)}</td><td class="num"><b>${counts ? sar(tt - pd) : "—"}</b></td>
        <td>${pill(st)}</td>
        <td><div class="row-actions">
          ${i.draft ? `<button class="btn sm" data-act="edit-invoice" data-id="${i.id}">Edit</button><button class="btn sm danger" data-act="del-invoice" data-id="${i.id}">Delete</button>` : ""}
          ${counts && tt - pd > 0 ? `<button class="btn sm" data-act="new-payment" data-id="${i.id}">Payment</button>` : ""}
        </div></td></tr>`;
    }).join("");
    return `
      <div class="page-head"><div><h1>Invoices</h1><p>Issued invoices are locked. To correct one, void it and issue a new invoice.</p></div>
      <div class="actions"><button class="btn primary" data-act="new-invoice">New invoice</button></div></div>
      <div class="toolbar"><div class="chips">${chips}</div></div>
      <div class="toolbar"><input type="search" id="inv-search" placeholder="Search number or customer" value="${esc(viewState.invQuery)}"></div>
      <div class="table-wrap">${rows ? `<table><thead><tr><th>Number</th><th>Customer</th><th class="num">Date</th><th class="num">Due</th><th class="num">Total</th><th class="num">Paid</th><th class="num">Balance</th><th>Status</th><th></th></tr></thead>
        <tbody>${rows}</tbody><tfoot><tr><td colspan="4">Issued invoices shown</td><td class="num">${sar(sumT)}</td><td class="num">${sar(sumP)}</td><td class="num">${sar(sumB)}</td><td colspan="2"></td></tr></tfoot></table>` : `<div class="empty">No invoices match this filter.</div>`}</div>`;
  };

  function lineRow(l = {}) {
    return `<tr>
      <td><input name="desc" value="${esc(l.desc)}" placeholder="Description" aria-label="Description"></td>
      <td><input name="lqty" type="number" step="any" min="0" value="${esc(l.qty ?? 1)}" aria-label="Quantity"></td>
      <td><input name="lprice" type="number" step="0.01" min="0" value="${esc(l.unitPrice ?? "")}" aria-label="Unit price"></td>
      <td class="num line-amt">0.00</td>
      <td><button type="button" class="icon-btn" data-rmline aria-label="Remove line">×</button></td></tr>`;
  }

  function readInvoiceForm(form) {
    const descs = $$("input[name=desc]", form).map((e) => e.value.trim());
    const qtys = $$("input[name=lqty]", form).map((e) => e.value);
    const prices = $$("input[name=lprice]", form).map((e) => e.value);
    const lines = descs
      .map((desc, k) => ({ desc, qty: Number(qtys[k]) || 0, unitPrice: C.toSar(C.toHalalas(prices[k])) }))
      .filter((l) => l.desc || l.unitPrice);
    return {
      customerId: form.elements.customerId.value,
      date: form.elements.date.value,
      dueDate: form.elements.dueDate.value,
      discount: C.toSar(C.toHalalas(form.elements.discount.value || 0)),
      vatRate: Number(form.elements.vatRate.value),
      notes: form.elements.notes.value,
      lines,
    };
  }

  function updateInvoiceTotals(form) {
    const d = readInvoiceForm(form);
    $$("#lines tbody tr", form).forEach((tr) => {
      const q = $("input[name=lqty]", tr).value, p = $("input[name=lprice]", tr).value;
      $(".line-amt", tr).textContent = sar(C.lineAmount(q, p));
    });
    const t = C.invoiceTotals(d);
    $("#inv-totals", form).innerHTML = `
      <span>Subtotal</span><span class="num">${sar(t.subtotal)}</span>
      <span>Discount</span><span class="num">−${sar(t.discount)}</span>
      <span>Taxable amount</span><span class="num">${sar(t.taxable)}</span>
      <span>VAT ${t.rate}%</span><span class="num">${sar(t.vat)}</span>
      <span class="grand">Total SAR</span><span class="num grand">${sar(t.total)}</span>`;
  }

  function invoiceForm(inv, preset = {}) {
    if (!state.customers.length) {
      toast("Add a customer first.");
      return customerForm(null, () => invoiceForm(inv, preset));
    }
    const s = state.settings;
    const date = inv?.date || todayIso();
    const i = inv || { date, dueDate: addDays(date, s.paymentTerms), vatRate: s.vatRate, discount: 0, lines: [{}], ...preset };
    const issued = s.vatNumber && C.isValidVatNumber(s.vatNumber);
    openModal({
      title: inv ? "Edit draft invoice" : "New invoice",
      wide: true,
      body: `<div class="grid-form three">
          <label class="field"><span>Customer</span><select name="customerId">${customerOptions(i.customerId)}</select></label>
          <label class="field"><span>Invoice date</span><input name="date" type="date" value="${esc(i.date)}"></label>
          <label class="field"><span>Due date</span><input name="dueDate" type="date" value="${esc(i.dueDate)}"></label>
        </div>
        <div class="table-wrap" style="margin-top:14px"><table class="lines" id="lines"><thead><tr><th>Description</th><th>Qty</th><th>Unit price</th><th class="num">Amount</th><th></th></tr></thead>
          <tbody>${(i.lines.length ? i.lines : [{}]).map(lineRow).join("")}</tbody></table></div>
        <button type="button" class="btn sm" data-addline style="margin-top:8px">Add line</button>
        <div class="grid-form three" style="margin-top:14px">
          <label class="field"><span>Discount, SAR (before VAT)</span><input name="discount" type="number" min="0" step="0.01" value="${esc(i.discount || 0)}"></label>
          <label class="field"><span>VAT rate %</span><select name="vatRate">${[15, 0].map((r) => `<option value="${r}" ${Number(i.vatRate) === r ? "selected" : ""}>${r}%${r === 0 ? " (zero-rated / exempt)" : ""}</option>`).join("")}</select></label>
          <div id="inv-totals" class="totals"></div>
          <label class="field full"><span>Notes printed on invoice</span><textarea name="notes" placeholder="Delivery details, PO number">${esc(i.notes)}</textarea></label>
        </div>
        ${issued ? "" : `<p class="error">Your company VAT number is missing or invalid. Add it in Settings before issuing tax invoices.</p>`}`,
      foot: `<span class="hint">Issuing assigns the next invoice number and locks the invoice.</span><span class="spacer"></span>
        <button type="button" class="btn" data-close>Cancel</button>
        <button type="submit" class="btn" value="draft">Save draft</button>
        <button type="submit" class="btn primary" value="issue">Issue invoice</button>`,
      onOpen: (body) => {
        const form = $("#modal-form");
        updateInvoiceTotals(form);
        body.oninput = () => updateInvoiceTotals(form);
        body.onclick = (e) => {
          if (e.target.closest("[data-addline]")) {
            $("#lines tbody", body).insertAdjacentHTML("beforeend", lineRow());
            $("#lines tbody tr:last-child input", body).focus();
          }
          const rm = e.target.closest("[data-rmline]");
          if (rm) {
            if ($$("#lines tbody tr", body).length > 1) rm.closest("tr").remove();
            updateInvoiceTotals(form);
          }
        };
      },
      onSubmit: (_fd, action, form) => {
        const d = readInvoiceForm(form);
        if (!d.lines.length) return fieldError(form, "desc", "Add at least one invoice line.");
        if (d.lines.some((l) => !l.desc)) return fieldError(form, "desc", "Every line needs a description.");
        if (!d.date) return fieldError(form, "date", "Enter the invoice date.");
        if (d.dueDate && d.dueDate < d.date) return fieldError(form, "dueDate", "The due date cannot be before the invoice date.");
        if (action === "issue" && !issued) { toast("Add a valid company VAT number in Settings first."); return false; }
        if (action === "issue" && C.invoiceTotals(d).total <= 0) { toast("An invoice total must be more than zero."); return false; }
        let target = inv;
        if (!target) {
          target = { id: uid(), number: "", jobIds: preset.jobIds || [], draft: true, void: false };
          state.invoices.push(target);
        }
        Object.assign(target, d);
        (target.jobIds || []).forEach((jid) => { const j = byId(state.jobs, jid); if (j) j.invoiceId = target.id; });
        if (action === "issue") issueInvoice(target);
        save();
        render();
        toast(action === "issue" ? `Invoice ${target.number} issued` : "Draft saved");
        if (action === "issue") setTimeout(() => viewInvoice(target), 50);
      },
    });
  }

  function issueInvoice(inv) {
    state.seq.invoice += 1;
    inv.number = `${state.settings.invoicePrefix || "INV"}-${inv.date.slice(0, 4)}-${pad(state.seq.invoice)}`;
    inv.draft = false;
    inv.issuedAt = new Date().toISOString().replace(/\.\d{3}Z$/, "Z");
  }

  function invoiceFromJob(job) {
    invoiceForm(null, {
      customerId: job.customerId,
      jobIds: [job.id],
      lines: [{
        desc: `${job.title} — ${Number(job.qty || 0).toLocaleString("en-US")} pcs${[job.size, job.material, job.colors, job.finishing].filter(Boolean).map((x) => ", " + x).join("")}`,
        qty: 1,
        unitPrice: job.price,
      }],
      notes: `Job ref: ${job.jobNo}`,
    });
  }

  function qrDataUrl(text) {
    try {
      if (!window.QRCode) return null;
      const box = document.createElement("div");
      new window.QRCode(box, { text, width: 236, height: 236, correctLevel: window.QRCode.CorrectLevel.M });
      const canvas = box.querySelector("canvas");
      return canvas ? canvas.toDataURL("image/png") : null;
    } catch (e) {
      return null;
    }
  }

  function invoiceDoc(inv) {
    const s = state.settings;
    const c = customer(inv.customerId);
    const t = invTotals(inv);
    const paid = invPaid(inv);
    const st = invStatus(inv);
    const simplified = !c.vatNumber;
    const title = inv.draft ? "DRAFT — not a valid tax invoice" : simplified ? "Simplified Tax Invoice" : "Tax Invoice";
    const titleAr = inv.draft ? "مسودة" : simplified ? "فاتورة ضريبية مبسطة" : "فاتورة ضريبية";
    let qr = "";
    if (!inv.draft && !inv.void) {
      const url = qrDataUrl(C.zatcaTlv({ sellerName: s.companyName, vatNumber: s.vatNumber, timestamp: inv.issuedAt || inv.date + "T00:00:00Z", total: C.toSar(t.total).toFixed(2), vat: C.toSar(t.vat).toFixed(2) }));
      qr = url ? `<div class="qr"><img src="${url}" alt="ZATCA QR code"></div>` : `<div class="qr small muted">QR unavailable offline</div>`;
    }
    const lines = inv.lines.map((l, k) => {
      const amt = C.lineAmount(l.qty, l.unitPrice);
      return `<tr><td>${k + 1}</td><td>${esc(l.desc)}</td><td class="num">${l.qty}</td><td class="num">${sar(C.toHalalas(l.unitPrice))}</td><td class="num">${sar(amt)}</td></tr>`;
    }).join("");
    return `<div class="doc">
      <div class="doc-top">
        <div>
          <h1>${esc(s.companyName)}</h1>
          ${s.companyNameAr ? `<div class="ar">${esc(s.companyNameAr)}</div>` : ""}
          <div>${esc(s.address)}</div>
          <div>VAT No. <span class="mono">${esc(s.vatNumber || "—")}</span>${s.crNumber ? ` · CR <span class="mono">${esc(s.crNumber)}</span>` : ""}</div>
          <div>${esc([s.phone, s.email].filter(Boolean).join(" · "))}</div>
          <div class="strip"><i style="background:#00a3d9"></i><i style="background:#d6246e"></i><i style="background:#f2c200"></i><i style="background:#16191f"></i></div>
        </div>
        <div style="text-align:right">
          <h1>${title}</h1><div class="ar">${titleAr}</div>
          <div class="mono" style="font-size:15px;margin-top:6px">${esc(inv.number || "—")}</div>
          ${inv.void ? `<div style="color:#b3261e;font-weight:700;font-size:16px">VOID</div>` : ""}
        </div>
      </div>
      <div class="doc-meta">
        <dl><dt>Bill to</dt><dd><b>${esc(c.name)}</b></dd>
          ${c.vatNumber ? `<dt>VAT No.</dt><dd class="mono">${esc(c.vatNumber)}</dd>` : ""}
          ${c.crNumber ? `<dt>CR No.</dt><dd class="mono">${esc(c.crNumber)}</dd>` : ""}
          ${c.address ? `<dt>Address</dt><dd>${esc(c.address)}</dd>` : ""}</dl>
        <dl><dt>Issue date</dt><dd>${fmtDate(inv.date)}</dd><dt>Supply date</dt><dd>${fmtDate(inv.date)}</dd><dt>Due date</dt><dd>${fmtDate(inv.dueDate)}</dd><dt>Currency</dt><dd>SAR</dd></dl>
      </div>
      <table><thead><tr><th>#</th><th>Description</th><th class="num">Qty</th><th class="num">Unit price</th><th class="num">Amount excl. VAT</th></tr></thead><tbody>${lines}</tbody></table>
      <div class="doc-foot">
        ${qr || "<div></div>"}
        <div class="totals">
          <span>Subtotal · <span class="ar">المجموع</span></span><span class="num">${sar(t.subtotal)}</span>
          ${t.discount ? `<span>Discount · <span class="ar">خصم</span></span><span class="num">−${sar(t.discount)}</span>` : ""}
          <span>Taxable amount · <span class="ar">المبلغ الخاضع للضريبة</span></span><span class="num">${sar(t.taxable)}</span>
          <span>VAT ${t.rate}% · <span class="ar">ضريبة القيمة المضافة</span></span><span class="num">${sar(t.vat)}</span>
          <span class="grand">Total incl. VAT · <span class="ar">الإجمالي</span></span><span class="num grand">${sar(t.total)}</span>
          ${paid ? `<span>Paid</span><span class="num">−${sar(paid)}</span><span class="grand">Balance due</span><span class="num grand">${sar(t.total - paid)}</span>` : ""}
        </div>
      </div>
      ${inv.notes ? `<p class="note"><b>Notes:</b> ${esc(inv.notes)}</p>` : ""}
      ${s.iban ? `<p class="note"><b>Bank transfer:</b> ${esc(s.bankName)} · IBAN <span class="mono">${esc(s.iban)}</span> · Please quote ${esc(inv.number)}</p>` : ""}
      <p class="note">Status: ${esc(st)}</p>
    </div>`;
  }

  function viewInvoice(inv) {
    const bal = invBalance(inv);
    const hasPay = invPaid(inv) > 0;
    const payRows = state.payments.filter((p) => p.invoiceId === inv.id).sort((a, b) => a.date.localeCompare(b.date))
      .map((p) => `<tr><td class="mono small">${esc(p.receiptNo)}</td><td>${fmtDate(p.date)}</td><td>${esc(p.method)}</td><td>${esc(p.reference)}</td><td class="num">${sar(C.toHalalas(p.amount))}</td></tr>`).join("");
    openModal({
      title: inv.number || "Draft invoice",
      wide: true,
      body: invoiceDoc(inv) + (payRows ? `<h3 style="margin:16px 0 6px">Payments received</h3><div class="table-wrap"><table><tbody>${payRows}</tbody></table></div>` : ""),
      foot: `
        ${!inv.draft && !inv.void && !hasPay ? `<button type="button" class="btn danger" data-act="void-invoice" data-id="${inv.id}">Void</button>` : ""}
        ${inv.draft ? `<button type="button" class="btn" data-act="edit-invoice" data-id="${inv.id}">Edit draft</button>` : ""}
        <span class="spacer"></span>
        <button type="button" class="btn" data-act="share-invoice" data-id="${inv.id}">Share on WhatsApp</button>
        <button type="button" class="btn" data-act="print-invoice" data-id="${inv.id}">Print / PDF</button>
        ${!inv.draft && !inv.void && bal > 0 ? `<button type="button" class="btn primary" data-act="new-payment" data-id="${inv.id}">Record payment</button>` : `<button type="button" class="btn primary" data-close>Close</button>`}`,
    });
  }

  function printInvoice(inv) {
    $("#print-root").innerHTML = invoiceDoc(inv);
    const prev = document.title;
    document.title = inv.number || "invoice";
    window.print();
    document.title = prev;
  }

  /* ================= payments ================= */
  routes.payments = () => {
    let total = 0;
    const rows = state.payments.slice().sort((a, b) => b.date.localeCompare(a.date) || b.receiptNo.localeCompare(a.receiptNo)).map((p) => {
      const inv = byId(state.invoices, p.invoiceId);
      const amt = C.toHalalas(p.amount);
      total += amt;
      return `<tr><td class="mono small">${esc(p.receiptNo)}</td><td>${fmtDate(p.date)}</td>
        <td>${inv ? `<button class="link-btn mono" data-act="view-invoice" data-id="${inv.id}">${esc(inv.number)}</button>` : `<span class="error">Missing invoice</span>`}</td>
        <td>${esc(inv ? customer(inv.customerId).name : "—")}</td><td>${esc(p.method)}</td><td class="small">${esc(p.reference)}</td>
        <td class="num">${sar(amt)}</td>
        <td><div class="row-actions"><button class="btn sm danger" data-act="del-payment" data-id="${p.id}">Delete</button></div></td></tr>`;
    }).join("");
    return `
      <div class="page-head"><div><h1>Payments</h1><p>Money received against invoices.</p></div>
      <div class="actions"><button class="btn primary" data-act="new-payment">Record payment</button></div></div>
      <div class="table-wrap">${rows ? `<table><thead><tr><th>Receipt</th><th>Date</th><th>Invoice</th><th>Customer</th><th>Method</th><th>Reference</th><th class="num">Amount</th><th></th></tr></thead>
        <tbody>${rows}</tbody><tfoot><tr><td colspan="6">Total received (SAR)</td><td class="num">${sar(total)}</td><td></td></tr></tfoot></table>` : `<div class="empty">No payments yet. Record one from an open invoice.</div>`}</div>`;
  };

  function paymentForm(invoiceId) {
    const open = state.invoices.filter((i) => C.isIssued(i) && invBalance(i) > 0);
    if (!open.length) { toast("There are no open invoices to pay."); return; }
    const sel = invoiceId && open.find((i) => i.id === invoiceId) ? invoiceId : open[0].id;
    const opts = open.sort((a, b) => a.date.localeCompare(b.date)).map((i) => `<option value="${i.id}" ${i.id === sel ? "selected" : ""}>${esc(i.number)} — ${esc(customer(i.customerId).name)} — balance ${sar(invBalance(i))}</option>`).join("");
    openModal({
      title: "Record payment",
      body: `<div class="grid-form">
        <label class="field full"><span>Invoice</span><select name="invoiceId">${opts}</select></label>
        <label class="field"><span>Amount, SAR</span><input name="amount" type="number" min="0.01" step="0.01" value="${C.toSar(invBalance(byId(state.invoices, sel))).toFixed(2)}"></label>
        <label class="field"><span>Date received</span><input name="date" type="date" value="${todayIso()}"></label>
        <label class="field"><span>Method</span><select name="method">${C.PAYMENT_METHODS.map((m) => `<option>${m}</option>`).join("")}</select></label>
        <label class="field"><span>Reference</span><input name="reference" placeholder="Transfer ref, cheque no."></label>
      </div>`,
      onOpen: (body) => {
        body.querySelector("[name=invoiceId]").onchange = (e) => {
          body.querySelector("[name=amount]").value = C.toSar(invBalance(byId(state.invoices, e.target.value))).toFixed(2);
        };
      },
      onSubmit: (fd, _a, form) => {
        const d = Object.fromEntries(fd);
        const inv = byId(state.invoices, d.invoiceId);
        const amt = C.toHalalas(d.amount);
        if (amt <= 0) return fieldError(form, "amount", "Enter an amount above zero.");
        if (amt > invBalance(inv)) return fieldError(form, "amount", `That is more than the open balance of ${sar(invBalance(inv))}.`);
        if (!d.date) return fieldError(form, "date", "Enter the date the money was received.");
        state.seq.receipt += 1;
        state.payments.push({ id: uid(), receiptNo: `RCT-${pad(state.seq.receipt)}`, invoiceId: inv.id, date: d.date, amount: C.toSar(amt), method: d.method, reference: d.reference.trim() });
        save();
        render();
        toast(`Payment of ${sar(amt)} recorded on ${inv.number}`);
      },
    });
  }

  /* ================= reports ================= */
  routes.reports = () => {
    const t = todayIso();
    const from = viewState.repFrom || t.slice(0, 8) + "01";
    const to = viewState.repTo || t;
    const r = C.reconcile(state.invoices, state.payments);
    const ag = C.aging(state.invoices, state.payments, t);
    const ok = r.residual === 0 && !r.orphanPayments.length && !r.overpaid.length && ag.total === r.outstanding;

    // aging by customer
    const byCust = new Map();
    for (const c of state.customers) {
      const invs = state.invoices.filter((i) => i.customerId === c.id);
      const a = C.aging(invs, state.payments, t);
      if (a.total) byCust.set(c, a);
    }
    const agRows = [...byCust].sort((a, b) => b[1].total - a[1].total).map(([c, a]) =>
      `<tr><td>${esc(c.name)}</td>${["current", "d1_30", "d31_60", "d61_90", "d90p"].map((k) => `<td class="num ${k !== "current" && a[k] ? "error" : ""}">${a[k] ? sar(a[k]) : "–"}</td>`).join("")}<td class="num"><b>${sar(a.total)}</b></td></tr>`).join("");

    // VAT (output) for period
    const periodInv = state.invoices.filter((i) => C.isIssued(i) && i.date >= from && i.date <= to);
    const months = new Map();
    for (const i of periodInv) {
      const m = i.date.slice(0, 7);
      const x = months.get(m) || { n: 0, taxable: 0, vat: 0, total: 0 };
      const tt = invTotals(i);
      x.n++; x.taxable += tt.taxable; x.vat += tt.vat; x.total += tt.total;
      months.set(m, x);
    }
    const vt = { n: 0, taxable: 0, vat: 0, total: 0 };
    const vatRows = [...months].sort().map(([m, x]) => {
      vt.n += x.n; vt.taxable += x.taxable; vt.vat += x.vat; vt.total += x.total;
      return `<tr><td>${m}</td><td class="num">${x.n}</td><td class="num">${sar(x.taxable)}</td><td class="num">${sar(x.vat)}</td><td class="num">${sar(x.total)}</td></tr>`;
    }).join("");
    const vatCheck = vt.taxable + vt.vat === vt.total;

    // collections in period by method
    const pays = state.payments.filter((p) => p.date >= from && p.date <= to);
    const byMethod = C.PAYMENT_METHODS.map((m) => [m, pays.filter((p) => p.method === m).reduce((s, p) => s + C.toHalalas(p.amount), 0)]).filter(([, v]) => v);
    const payTotal = byMethod.reduce((s, [, v]) => s + v, 0);

    // job profitability — delivered in period (by due date as delivery proxy)
    const done = state.jobs.filter((j) => j.status === "Delivered" && j.dueDate >= from && j.dueDate <= to);
    const rev = done.reduce((s, j) => s + C.toHalalas(j.price), 0);
    const cost = done.reduce((s, j) => s + C.toHalalas(j.cost), 0);

    return `
      <div class="page-head"><div><h1>Reports</h1><p>Receivables are as of today. VAT, collections and job margin follow the period below.</p></div>
        <div class="actions">
          <label class="field"><span>From</span><input type="date" id="rep-from" value="${from}"></label>
          <label class="field"><span>To</span><input type="date" id="rep-to" value="${to}"></label>
        </div></div>

      <section class="panel">
        <div class="panel-head"><h2>Receivables tie-out</h2><span class="check ${ok ? "ok" : "fail"}">${ok ? "✓ Balanced" : "✗ Exceptions found"}</span></div>
        <div class="panel-body">
          <div class="tieout">
            <span>Issued invoices, incl. VAT</span><span class="num">${sar(r.invoiced)}</span>
            <span>Less: payments received on them</span><span class="num">−${sar(r.collected)}</span>
            <span class="sum">Expected receivables</span><span class="num sum">${sar(r.invoiced - r.collected)}</span>
            <span>Sum of open invoice balances</span><span class="num">${sar(r.outstanding)}</span>
            <span>Aging report total</span><span class="num">${sar(ag.total)}</span>
            <span class="sum">Residual</span><span class="num sum ${r.residual ? "error" : ""}">${sar(r.residual)}</span>
          </div>
          ${r.orphanPayments.length ? `<p class="error">${r.orphanPayments.length} payment(s) point to a draft, void or missing invoice: ${r.orphanPayments.map((p) => esc(p.receiptNo)).join(", ")}.</p>` : ""}
          ${r.overpaid.length ? `<p class="error">Overpaid invoice(s): ${r.overpaid.map((i) => esc(i.number)).join(", ")}.</p>` : ""}
          ${ag.total !== r.outstanding ? `<p class="error">Aging total differs from open balances by ${sar(ag.total - r.outstanding)} (an overpayment reduces open balances but is excluded from aging).</p>` : ""}
        </div>
      </section>

      <section class="panel" style="margin-top:16px">
        <div class="panel-head"><h2>Aging by customer</h2><span class="muted small">Days past due date · SAR</span></div>
        <div class="table-wrap">${agRows ? `<table><thead><tr><th>Customer</th><th class="num">Current</th><th class="num">1–30</th><th class="num">31–60</th><th class="num">61–90</th><th class="num">90+</th><th class="num">Total</th></tr></thead><tbody>${agRows}</tbody>
          <tfoot><tr><td>Total</td>${["current", "d1_30", "d31_60", "d61_90", "d90p"].map((k) => `<td class="num">${sar(ag[k])}</td>`).join("")}<td class="num">${sar(ag.total)}</td></tr></tfoot></table>` : `<div class="empty">Nothing outstanding.</div>`}</div>
      </section>

      <div class="cols">
        <section class="panel">
          <div class="panel-head"><h2>Output VAT</h2><span class="check ${vatCheck ? "ok" : "fail"} small">${vatCheck ? "✓ Taxable + VAT = Total" : "✗ Totals do not add up"}</span></div>
          <div class="table-wrap">${vatRows ? `<table><thead><tr><th>Month</th><th class="num">Invoices</th><th class="num">Taxable</th><th class="num">VAT</th><th class="num">Total</th></tr></thead><tbody>${vatRows}</tbody>
            <tfoot><tr><td>Period</td><td class="num">${vt.n}</td><td class="num">${sar(vt.taxable)}</td><td class="num">${sar(vt.vat)}</td><td class="num">${sar(vt.total)}</td></tr></tfoot></table>` : `<div class="empty">No invoices issued in this period.</div>`}</div>
          <p class="hint" style="padding:8px 16px 14px;margin:0">Sales side only. Input VAT on purchases is not tracked here. Void invoices are excluded.</p>
        </section>
        <section class="panel">
          <div class="panel-head"><h2>Collections & job margin</h2></div>
          <div class="panel-body">
            <div class="tieout">
              ${byMethod.map(([m, v]) => `<span>${esc(m)}</span><span class="num">${sar(v)}</span>`).join("") || `<span class="muted">No payments in period</span><span></span>`}
              <span class="sum">Collected in period</span><span class="num sum">${sar(payTotal)}</span>
            </div>
            <div class="tieout" style="margin-top:18px">
              <span>Delivered jobs (${done.length}) — revenue excl. VAT</span><span class="num">${sar(rev)}</span>
              <span>Estimated cost</span><span class="num">−${sar(cost)}</span>
              <span class="sum">Gross margin${rev ? ` (${((100 * (rev - cost)) / rev).toFixed(1)}%)` : ""}</span><span class="num sum">${sar(rev - cost)}</span>
            </div>
          </div>
        </section>
      </div>

      <section class="panel" style="margin-top:16px">
        <div class="panel-head"><h2>Export</h2></div>
        <div class="panel-body actions">
          <button class="btn" data-act="csv" data-kind="invoices">Invoices CSV</button>
          <button class="btn" data-act="csv" data-kind="payments">Payments CSV</button>
          <button class="btn" data-act="csv" data-kind="jobs">Jobs CSV</button>
          <button class="btn" data-act="csv" data-kind="customers">Customers CSV</button>
          <span class="hint">CSV opens in Excel. Amounts are in SAR with 2 decimals.</span>
        </div>
      </section>`;
  };

  function exportCsv(kind) {
    const d = todayIso();
    const h = (x) => C.toSar(x).toFixed(2);
    let rows;
    if (kind === "invoices") {
      rows = [["Number", "Status", "Customer", "Customer VAT", "Date", "Due date", "Subtotal", "Discount", "Taxable", "VAT", "Total", "Paid", "Balance"]];
      state.invoices.forEach((i) => {
        const t = invTotals(i), p = invPaid(i), c = customer(i.customerId);
        rows.push([i.number || "DRAFT", invStatus(i), c.name, c.vatNumber || "", i.date, i.dueDate, h(t.subtotal), h(t.discount), h(t.taxable), h(t.vat), h(t.total), h(p), h(t.total - p)]);
      });
    } else if (kind === "payments") {
      rows = [["Receipt", "Date", "Invoice", "Customer", "Method", "Reference", "Amount"]];
      state.payments.forEach((p) => {
        const i = byId(state.invoices, p.invoiceId);
        rows.push([p.receiptNo, p.date, i?.number || "", i ? customer(i.customerId).name : "", p.method, p.reference, h(C.toHalalas(p.amount))]);
      });
    } else if (kind === "jobs") {
      rows = [["Job", "Customer", "Title", "Product", "Qty", "Size", "Material", "Colours", "Finishing", "Status", "Created", "Due", "Price", "Est. cost", "Invoice"]];
      state.jobs.forEach((j) => rows.push([j.jobNo, customer(j.customerId).name, j.title, j.product, j.qty, j.size, j.material, j.colors, j.finishing, j.status, j.createdAt, j.dueDate, h(C.toHalalas(j.price)), h(C.toHalalas(j.cost)), byId(state.invoices, j.invoiceId)?.number || ""]));
    } else {
      rows = [["Name", "VAT number", "CR number", "Phone", "Email", "Address"]];
      state.customers.forEach((c) => rows.push([c.name, c.vatNumber, c.crNumber, c.phone, c.email, c.address]));
    }
    download(`${kind}-${d}.csv`, toCsv(rows));
  }

  /* ================= settings ================= */
  routes.settings = () => {
    const s = state.settings;
    const f = (name, label, attrs = "") => `<label class="field"><span>${label}</span><input id="set-${name}" name="${name}" value="${esc(s[name])}" ${attrs}></label>`;
    return `
      <div class="page-head"><div><h1>Settings</h1><p>These details print on every invoice.</p></div></div>
      <form id="settings-form" class="panel"><div class="panel-body" style="padding-top:16px">
        <div class="grid-form">
          ${f("companyName", "Company name (English)", "required")}
          ${f("companyNameAr", "Company name (Arabic)", 'dir="rtl"')}
          ${f("vatNumber", "VAT registration number", 'inputmode="numeric" maxlength="15" placeholder="3XXXXXXXXXXXXX3"')}
          ${f("crNumber", "Commercial registration (CR)")}
          <label class="field full"><span>Address</span><input id="set-address" name="address" value="${esc(s.address)}"></label>
          ${f("phone", "Phone")}${f("email", "Email", 'type="email"')}
          ${f("bankName", "Bank name")}${f("iban", "IBAN")}
          ${f("invoicePrefix", "Invoice number prefix")}
          ${f("paymentTerms", "Payment terms (days)", 'type="number" min="0" step="1"')}
        </div>
        <div class="actions" style="margin-top:14px"><button class="btn primary" type="submit">Save settings</button></div>
      </div></form>

      <section class="panel" style="margin-top:16px">
        <div class="panel-head"><h2>Your data</h2></div>
        <div class="panel-body">
          <p class="hint" style="margin-top:0">Data is saved in this browser only. Export a backup regularly and keep it with your records; import it to move to another computer.</p>
          <div class="actions">
            <button class="btn" data-act="export-json">Export backup</button>
            <label class="btn" for="import-file">Import backup</label>
            <input type="file" id="import-file" accept="application/json,.json" hidden>
            ${state.demo ? `<button class="btn primary" data-act="clear-demo">Clear sample data and start</button>` : `<button class="btn" data-act="load-demo">Load sample data</button>`}
            <button class="btn danger" data-act="reset">Delete all data</button>
          </div>
        </div>
      </section>
      <p class="hint" style="margin-top:14px">The invoice QR code follows the ZATCA Phase 1 (generation) format. Phase 2 (integration) requires invoices to be cleared or reported through the Fatoora platform with a cryptographic stamp, which needs a certified e-invoicing solution.</p>`;
  };

  /* ================= global event delegation ================= */
  document.addEventListener("click", (e) => {
    const f = e.target.closest("[data-jobfilter]");
    if (f) {
      viewState.jobFilter = f.dataset.jobfilter;
      if (current === "jobs") { e.preventDefault(); render(); }
      return;
    }
    const iv = e.target.closest("[data-invfilter]");
    if (iv) { viewState.invFilter = iv.dataset.invfilter; render(); return; }

    const b = e.target.closest("[data-act]");
    if (!b) return;
    const id = b.dataset.id;
    switch (b.dataset.act) {
      case "new-job": return jobForm(null, id ? { customerId: id } : {});
      case "view-job": return viewJob(byId(state.jobs, id));
      case "view-customer": location.hash = "#customer/" + id; return;
      case "advance-job": {
        const j = byId(state.jobs, id);
        const next = C.jobProgress(j.status).next;
        if (!next) return;
        const wasOpen = modal.open;
        setStatus(j, next);
        save();
        render();
        if (wasOpen) viewJob(j);
        return toast(next === "Delivered" ? `${j.jobNo} completed${j.invoiceId ? "" : ". Create the invoice next."}` : `${j.jobNo} moved to ${next}`);
      }
      case "share-job": { const j = byId(state.jobs, id); return shareModal(j.jobNo, jobShareText(j), customer(j.customerId).phone, () => viewJob(j)); }
      case "share-invoice": { const i = byId(state.invoices, id); return shareModal(i.number || "Draft", invoiceShareText(i), customer(i.customerId).phone, () => viewInvoice(i)); }
      case "share-customer": { const c = byId(state.customers, id); return shareModal(c.name, customerShareText(c), c.phone); }
      case "share-back": return shareBack ? shareBack() : closeModal();
      case "share-copy":
        return copyText($("#share-text").value).then((ok) => toast(ok ? "Copied. Paste it into WhatsApp." : "Select the text and copy it manually."));
      case "edit-job": return jobForm(byId(state.jobs, id));
      case "del-job": {
        const j = byId(state.jobs, id);
        if (j.invoiceId && byId(state.invoices, j.invoiceId)) return toast("This job is on an invoice. Delete or void the invoice first.");
        return confirmBox("Delete job", `Delete <b>${esc(j.jobNo)} ${esc(j.title)}</b>? This cannot be undone.`, "Delete job", () => {
          state.jobs = state.jobs.filter((x) => x.id !== id); save(); render(); toast("Job deleted");
        });
      }
      case "invoice-job": return invoiceFromJob(byId(state.jobs, id));
      case "new-customer": return customerForm();
      case "edit-customer": return customerForm(byId(state.customers, id));
      case "del-customer": {
        const c = byId(state.customers, id);
        if (state.jobs.some((j) => j.customerId === id) || state.invoices.some((i) => i.customerId === id)) return toast("This customer has jobs or invoices and cannot be deleted.");
        return confirmBox("Delete customer", `Delete <b>${esc(c.name)}</b>?`, "Delete customer", () => {
          state.customers = state.customers.filter((x) => x.id !== id); save(); render(); toast("Customer deleted");
        });
      }
      case "statement": return statement(byId(state.customers, id));
      case "csv-statement": {
        const { c, entries } = statement.last;
        let bal = 0;
        const rows = [["Date", "Ref", "Details", "Debit", "Credit", "Balance"], ...entries.map((x) => { bal += x.dr - x.cr; return [x.date, x.ref, x.desc, (x.dr / 100).toFixed(2), (x.cr / 100).toFixed(2), (bal / 100).toFixed(2)]; })];
        return download(`statement-${c.name.replace(/\W+/g, "-")}-${todayIso()}.csv`, toCsv(rows));
      }
      case "new-invoice": return invoiceForm(null, id ? { customerId: id } : {});
      case "edit-invoice": return invoiceForm(byId(state.invoices, id));
      case "view-invoice": return viewInvoice(byId(state.invoices, id));
      case "print-invoice": return printInvoice(byId(state.invoices, id));
      case "del-invoice": {
        const inv = byId(state.invoices, id);
        return confirmBox("Delete draft", "Delete this draft invoice?", "Delete draft", () => {
          state.jobs.forEach((j) => { if (j.invoiceId === id) j.invoiceId = null; });
          state.invoices = state.invoices.filter((x) => x.id !== inv.id); save(); render(); toast("Draft deleted");
        });
      }
      case "void-invoice": {
        const inv = byId(state.invoices, id);
        return confirmBox("Void invoice", `Void <b>${esc(inv.number)}</b>? It stays on file marked VOID, its number is not reused, and linked jobs become available to invoice again. Under VAT rules a correction to an issued invoice is normally made with a credit note; use void only for invoices issued in error and not yet sent.`, "Void invoice", () => {
          inv.void = true;
          inv.voidedAt = new Date().toISOString();
          state.jobs.forEach((j) => { if (j.invoiceId === id) j.invoiceId = null; });
          save(); render(); toast(`${inv.number} voided`);
        });
      }
      case "new-payment": return paymentForm(id);
      case "del-payment": {
        const p = byId(state.payments, id);
        return confirmBox("Delete payment", `Delete receipt <b>${esc(p.receiptNo)}</b> for ${sar(C.toHalalas(p.amount))}? The invoice balance will go back up.`, "Delete payment", () => {
          state.payments = state.payments.filter((x) => x.id !== id); save(); render(); toast("Payment deleted");
        });
      }
      case "csv": return exportCsv(b.dataset.kind);
      case "export-json": return download(`printops-backup-${todayIso()}.json`, JSON.stringify(state, null, 2), "application/json");
      case "clear-demo":
        return confirmBox("Clear sample data", "Remove all sample customers, jobs, invoices and payments? Your company settings are kept, except the sample name and numbers.", "Clear and start", () => {
          state = emptyState(); save(); location.hash = "#settings"; render(); toast("Sample data cleared. Enter your company details.");
        });
      case "load-demo":
        return confirmBox("Load sample data", "Replace everything with sample data? Export a backup first if you want to keep your records.", "Load sample data", () => {
          state = demoState(); save(); render(); toast("Sample data loaded");
        });
      case "reset":
        return confirmBox("Delete all data", "Delete every customer, job, invoice, payment and setting in this browser? This cannot be undone.", "Delete everything", () => {
          state = emptyState(); save(); render(); toast("All data deleted");
        });
    }
  });

  document.addEventListener("change", (e) => {
    if (e.target.matches("[data-jobstatus]")) {
      const j = byId(state.jobs, e.target.dataset.jobstatus);
      setStatus(j, e.target.value);
      save();
      render();
      toast(j.status === "Delivered" && !j.invoiceId ? `${j.jobNo} delivered. Click Invoice to bill it.` : `${j.jobNo} → ${j.status}`);
    }
    if (e.target.id === "rep-from" || e.target.id === "rep-to") {
      viewState.repFrom = $("#rep-from").value;
      viewState.repTo = $("#rep-to").value;
      render();
    }
    if (e.target.id === "import-file" && e.target.files[0]) {
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const s = JSON.parse(reader.result);
          if (!Array.isArray(s.invoices) || !Array.isArray(s.payments) || !s.settings) throw new Error("bad");
          state = { ...emptyState(), ...s, settings: { ...DEFAULT_SETTINGS, ...s.settings } };
          save(); render(); toast("Backup imported");
        } catch (err) {
          toast("That file is not a PrintOps backup.");
        }
      };
      reader.readAsText(e.target.files[0]);
    }
  });

  document.addEventListener("input", (e) => {
    if (e.target.classList.contains("invalid")) e.target.classList.remove("invalid");
    if (e.target.id === "job-search" || e.target.id === "inv-search") {
      const key = e.target.id === "job-search" ? "jobQuery" : "invQuery";
      viewState[key] = e.target.value;
      const pos = e.target.selectionStart;
      render();
      const el = $("#" + e.target.id);
      el.focus();
      el.setSelectionRange(pos, pos);
    }
  });

  document.addEventListener("submit", (e) => {
    if (e.target.id !== "settings-form") return;
    e.preventDefault();
    const d = Object.fromEntries(new FormData(e.target));
    d.vatNumber = (d.vatNumber || "").replace(/\s/g, "");
    if (!d.companyName.trim()) return fieldError(e.target, "companyName", "Enter your company name.");
    if (d.vatNumber && !C.isValidVatNumber(d.vatNumber)) return fieldError(e.target, "vatNumber", "A Saudi VAT number has 15 digits and starts and ends with 3.");
    d.paymentTerms = Math.max(0, parseInt(d.paymentTerms, 10) || 0);
    d.invoicePrefix = (d.invoicePrefix || "INV").trim().replace(/\s+/g, "-");
    Object.assign(state.settings, d);
    save();
    render();
    toast("Settings saved");
  });

  route();
})();
