// Run with:  node --test job-order-sync/tests/*.test.js
const test = require("node:test");
const assert = require("node:assert/strict");
const { applySync } = require("../Code.gs");

const rec = (id, updatedAt, extra = {}) => ({ id, updatedAt, ...extra });

test("first sync stores changes and returns them with revisions", () => {
  const data = {};
  const res = applySync(data, [{ t: "orders", r: rec("o1", "2026-10-01T08:00:00Z", { no: "JO-A-0001" }) }], 0);
  assert.equal(res.changed, true);
  assert.equal(data.rev, 1);
  assert.equal(res.records.length, 1);
  assert.equal(res.records[0].r._rev, 1);
});

test("a device only receives what changed after its last revision", () => {
  const data = {};
  applySync(data, [{ t: "orders", r: rec("o1", "2026-10-01T08:00:00Z") }], 0);
  applySync(data, [{ t: "customers", r: rec("c1", "2026-10-01T08:01:00Z") }], 1);
  const res = applySync(data, [], 1);
  assert.deepEqual(res.records.map((x) => x.r.id), ["c1"]);
});

test("newest edit wins; an older edit gets the newer copy back", () => {
  const data = {};
  applySync(data, [{ t: "orders", r: rec("o1", "2026-10-01T09:00:00Z", { status: "Ready" }) }], 0);
  const res = applySync(data, [{ t: "orders", r: rec("o1", "2026-10-01T08:00:00Z", { status: "Printing" }) }], 1);
  assert.equal(res.changed, false);
  assert.equal(data.records["orders:o1"].r.status, "Ready");
  assert.equal(res.records.length, 1, "rejected record is sent back even though rev <= since");
  assert.equal(res.records[0].r.status, "Ready");
});

test("deletions spread as tombstones", () => {
  const data = {};
  applySync(data, [{ t: "items", r: rec("i1", "2026-10-01T08:00:00Z") }], 0);
  const res = applySync(data, [{ t: "items", r: rec("i1", "2026-10-01T10:00:00Z", { deleted: true }) }], 1);
  assert.equal(res.records[0].r.deleted, true);
});

test("unknown types and records without id are ignored", () => {
  const data = {};
  const res = applySync(data, [{ t: "secrets", r: rec("x", "z") }, { t: "orders", r: { updatedAt: "z" } }, null], 0);
  assert.equal(res.changed, false);
  assert.equal(res.records.length, 0);
});

test("two devices with different series never collide and both survive", () => {
  const data = {};
  applySync(data, [{ t: "orders", r: rec("a", "2026-10-01T08:00:00Z", { no: "JO-A-0001" }) }], 0);
  const res = applySync(data, [{ t: "orders", r: rec("b", "2026-10-01T08:00:01Z", { no: "JO-B-0001" }) }], 0);
  assert.deepEqual(res.records.map((x) => x.r.no).sort(), ["JO-A-0001", "JO-B-0001"]);
});
