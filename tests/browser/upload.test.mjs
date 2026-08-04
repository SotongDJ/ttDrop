/* Uploads a 10 MiB file through the PWA UI and verifies OPFS staging,
 * the on-disk result hash, and the refreshed listing. */
import { launchBrowser, BASE, SERVE_DIR } from "./lib.mjs";
import { createHash, randomBytes } from "node:crypto";
import { writeFileSync, readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

const payload = randomBytes(10 * 1024 * 1024);
const srcPath = join(tmpdir(), "ttdrop-test-upload.dat");
writeFileSync(srcPath, payload);
const srcHash = createHash("sha256").update(payload).digest("hex");

const browser = await launchBrowser();
const page = await browser.newPage();
const errors = [];
page.on("pageerror", (e) => errors.push(`pageerror: ${e.message}`));
page.on("console", (m) => { if (m.type() === "error") errors.push(`console: ${m.text()}`); });

await page.goto(BASE, { waitUntil: "networkidle" });

const opfs = await page.evaluate(async () => {
  try { await navigator.storage.getDirectory(); return true; } catch { return false; }
});
console.log("OPFS available:", opfs);

await page.setInputFiles("#file-input", srcPath);
await page.waitForSelector("#send-list .status-ok", { timeout: 60000 });
const status = await page.textContent("#send-list .status-ok");
console.log("UI status:", status);

const finalName = status.replace(/^sent as /, "");
const destPath = join(SERVE_DIR, finalName);
if (!existsSync(destPath)) throw new Error(`uploaded file missing: ${destPath}`);
const destHash = createHash("sha256").update(readFileSync(destPath)).digest("hex");
console.log("hash match:", srcHash === destHash);

const listed = await page.textContent("#server-list");
console.log("listed in UI:", listed.includes(finalName));

if (errors.length) console.log("BROWSER ERRORS:", errors);
const ok = opfs && srcHash === destHash && listed.includes(finalName) && errors.length === 0;
console.log(ok ? "TEST PASS" : "TEST FAIL");
await browser.close();
process.exit(ok ? 0 : 1);
