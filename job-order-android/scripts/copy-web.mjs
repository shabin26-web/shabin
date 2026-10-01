// Copies the web app (index.html + vendor libraries) into www/ so Capacitor can package it.
import { mkdirSync, copyFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const src = join(root, "..", "job-order-app");
const www = join(root, "www");
mkdirSync(join(www, "vendor"), { recursive: true });
copyFileSync(join(src, "index.html"), join(www, "index.html"));
for (const f of readdirSync(join(src, "vendor"))) copyFileSync(join(src, "vendor", f), join(www, "vendor", f));
console.log("Copied job-order-app/index.html and vendor/ -> www/");
