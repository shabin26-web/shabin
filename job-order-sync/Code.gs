/**
 * Job Orders — sync script for one common Gmail account.
 *
 * Paste this whole file into a new Google Apps Script project (script.google.com) while signed
 * in to the common Gmail, set TEAM_PIN below, run setup() once, then deploy as a web app
 * (Execute as: Me, Who has access: Anyone). See README.md for the click-by-click steps.
 *
 * Data is kept in the Drive folder FOLDER_NAME:
 *   data.json                 every customer, item and order (the shared copy)
 *   backups/YYYY-MM-DD.json   one snapshot per day, the last KEEP_BACKUPS days are kept
 */

const TEAM_PIN = "CHANGE-ME";          // the PIN every device types in Settings → Sync
const FOLDER_NAME = "Job Orders Data";
const KEEP_BACKUPS = 90;
const TIME_ZONE = "Asia/Riyadh";
const TYPES = ["customers", "items", "orders", "settings"];

/* ---------- pure logic (also tested with Node) ---------- */

/**
 * Merge incoming changes into the shared data and return what the device is missing.
 * data:    { rev: number, records: { "type:id": { t, r } } }
 * changes: [{ t, r }]  where r has id and updatedAt (ISO time)
 * since:   the last rev this device received
 * The newest updatedAt wins. Deleted records stay as { deleted: true } so the deletion spreads.
 */
function applySync(data, changes, since) {
  data.rev = data.rev || 0;
  data.records = data.records || {};
  let changed = false;
  const rejected = [];
  (changes || []).forEach(function (c) {
    if (!c || TYPES.indexOf(c.t) < 0 || !c.r || !c.r.id) return;
    const key = c.t + ":" + c.r.id;
    const cur = data.records[key];
    if (!cur || String(c.r.updatedAt || "") >= String(cur.r.updatedAt || "")) {
      data.rev += 1;
      const rec = JSON.parse(JSON.stringify(c.r));
      rec._rev = data.rev;
      data.records[key] = { t: c.t, r: rec };
      changed = true;
    } else {
      rejected.push(key); // the shared copy is newer; send it back so the device catches up
    }
  });
  const out = [];
  Object.keys(data.records).forEach(function (k) {
    const x = data.records[k];
    if (x.r._rev > since || rejected.indexOf(k) >= 0) out.push(x);
  });
  return { changed: changed, records: out };
}

/* ---------- web app ---------- */

function doGet() {
  return ContentService.createTextOutput("Job Orders sync is running. Use this link in the app: Settings → Sync.");
}

function doPost(e) {
  let req;
  try { req = JSON.parse(e.postData.contents); } catch (err) { return json_({ ok: false, error: "Bad request." }); }
  if (!TEAM_PIN || /^change/i.test(TEAM_PIN)) return json_({ ok: false, error: "The sync script still has the default PIN. Set TEAM_PIN and deploy a new version." });
  if (String(req.pin || "") !== String(TEAM_PIN)) return json_({ ok: false, error: "Wrong team PIN." });
  if (req.action === "ping") return json_({ ok: true, folder: FOLDER_NAME, time: new Date().toISOString() });
  if (req.action !== "sync") return json_({ ok: false, error: "Unknown request." });

  const lock = LockService.getScriptLock();
  if (!lock.tryLock(25000)) return json_({ ok: false, error: "Another device is syncing. Try again in a moment." });
  try {
    const file = dataFile_();
    const text = file.getBlob().getDataAsString() || "{}";
    const data = JSON.parse(text);
    const res = applySync(data, req.changes || [], Number(req.since) || 0);
    if (res.changed) file.setContent(JSON.stringify(data));
    dailyBackup_(data);
    return json_({ ok: true, rev: data.rev || 0, records: res.records });
  } catch (err) {
    return json_({ ok: false, error: "Sync script error: " + err.message });
  } finally {
    lock.releaseLock();
  }
}

/** Run once from the editor: creates the Drive folder and data file. */
function setup() {
  const f = dataFile_();
  Logger.log("Ready. Data file: " + f.getUrl());
  Logger.log("Now click Deploy → New deployment → Web app (Execute as: Me, Who has access: Anyone).");
}

/* ---------- helpers ---------- */

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function folder_() {
  const it = DriveApp.getFoldersByName(FOLDER_NAME);
  return it.hasNext() ? it.next() : DriveApp.createFolder(FOLDER_NAME);
}

function dataFile_() {
  const folder = folder_();
  const it = folder.getFilesByName("data.json");
  return it.hasNext() ? it.next() : folder.createFile("data.json", JSON.stringify({ rev: 0, records: {} }), "application/json");
}

/** Save one snapshot a day into backups/ and keep the last KEEP_BACKUPS days. */
function dailyBackup_(data) {
  const parent = folder_();
  const fit = parent.getFoldersByName("backups");
  const folder = fit.hasNext() ? fit.next() : parent.createFolder("backups");
  const name = Utilities.formatDate(new Date(), TIME_ZONE, "yyyy-MM-dd") + ".json";
  if (folder.getFilesByName(name).hasNext()) return;
  folder.createFile(name, JSON.stringify(data), "application/json");
  const files = [];
  const all = folder.getFiles();
  while (all.hasNext()) files.push(all.next());
  files.sort(function (a, b) { return a.getName() < b.getName() ? 1 : -1; });
  files.slice(KEEP_BACKUPS).forEach(function (f) { f.setTrashed(true); });
}

if (typeof module !== "undefined") module.exports = { applySync: applySync };
