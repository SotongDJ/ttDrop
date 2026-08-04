/* Verifies the two-mode behavior: direct /files/ URLs render
 * whitelisted types inline in the browser, while clicking any file in
 * the app at / always goes through the managed download. */
import { launchBrowser, CONTEXT_OPTIONS, BASE, SERVE_DIR } from "./lib.mjs";
import { writeFileSync } from "node:fs";
import { join } from "node:path";

writeFileSync(join(SERVE_DIR, "readme-view.txt"), "inline works");
writeFileSync(join(SERVE_DIR, "opaque.bin"), Buffer.from([1, 2, 3, 4]));

const browser = await launchBrowser();
const context = await browser.newContext(CONTEXT_OPTIONS);
const page = await context.newPage();

let pass = 0;
let fail = 0;
const check = (label, cond) => {
  if (cond) { pass++; console.log(`PASS: ${label}`); }
  else { fail++; console.log(`FAIL: ${label}`); }
};

// Direct /files/ URL of a whitelisted type: renders inline, no download.
await page.goto(`${BASE}/files/readme-view.txt`, { waitUntil: "domcontentloaded" });
check("txt renders inline at /files/ URL",
  (await page.evaluate(() => document.body.innerText)).includes("inline works"));

// Direct /files/ URL of a non-whitelisted type: downloads (attachment).
const directDownload = page.waitForEvent("download", { timeout: 30000 });
await page.goto(`${BASE}/files/opaque.bin`).catch(() => {});
check("bin downloads at /files/ URL", (await directDownload).suggestedFilename() === "opaque.bin");

// In the app at /: clicking ANY file — including a viewable one —
// stays a managed download, never an inline view.
await page.goto(BASE, { waitUntil: "networkidle" });
const pagesBefore = context.pages().length;
await page.click(`#server-list a:text("readme-view.txt")`);
await page.waitForSelector("#receive-list .status-ok", { timeout: 60000 });
check("app click on txt is a managed download",
  (await page.textContent("#receive-list .status-ok")) === "saved");
check("no viewer tab opened from the app", context.pages().length === pagesBefore);

console.log(fail === 0 ? "TEST PASS" : "TEST FAIL");
await browser.close();
process.exit(fail === 0 ? 0 : 1);
