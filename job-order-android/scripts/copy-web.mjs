// Copies the single-file web app into www/ so Capacitor can package it.
import { mkdirSync, copyFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
mkdirSync(join(root, "www"), { recursive: true });
copyFileSync(join(root, "..", "job-order-app", "index.html"), join(root, "www", "index.html"));
console.log("Copied job-order-app/index.html -> www/index.html");
