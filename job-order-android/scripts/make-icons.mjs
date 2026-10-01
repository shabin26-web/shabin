// Renders the app icon and splash source images (run once; outputs are committed).
// Uses sharp, which comes with @capacitor/assets.
import sharp from "sharp";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const out = join(dirname(fileURLToPath(import.meta.url)), "..", "assets");
const blue = "#0a5fb4";
const mark = (size, tile) => `
<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">
  ${tile ? `<rect width="1024" height="1024" fill="${blue}"/>` : ""}
  <g fill="none" stroke="#ffffff" stroke-width="64" stroke-linecap="round" stroke-linejoin="round">
    <path d="M300 360 h200 v260 a110 110 0 0 1 -220 0"/>
    <circle cx="660" cy="512" r="150"/>
  </g>
  <rect x="300" y="740" width="424" height="28" rx="14" fill="#ffffff" opacity="0.55"/>
</svg>`;

await sharp(Buffer.from(mark(1024, true))).png().toFile(join(out, "icon-only.png"));
await sharp(Buffer.from(mark(1024, false))).png().toFile(join(out, "icon-foreground.png"));
await sharp({ create: { width: 1024, height: 1024, channels: 4, background: blue } }).png().toFile(join(out, "icon-background.png"));
const splash = `<svg xmlns="http://www.w3.org/2000/svg" width="2732" height="2732"><rect width="2732" height="2732" fill="${blue}"/><g transform="translate(1110 1110) scale(0.5)">${mark(1024, false).replace(/<\/?svg[^>]*>/g, "")}</g></svg>`;
await sharp(Buffer.from(splash)).png().toFile(join(out, "splash.png"));
await sharp(Buffer.from(splash)).png().toFile(join(out, "splash-dark.png"));
console.log("Icons written to assets/");
