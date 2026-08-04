/* Renames and deletes server files through the PWA UI (prompt/confirm
 * dialogs) and verifies the results on disk and in the listing. */
import { launchBrowser, CONTEXT_OPTIONS, BASE, SERVE_DIR } from "./lib.mjs";
import { writeFileSync, mkdirSync, existsSync, rmSync } from "node:fs";
import { join } from "node:path";

rmSync(join(SERVE_DIR, "ops"), { recursive: true, force: true });
mkdirSync(join(SERVE_DIR, "ops"), { recursive: true });
writeFileSync(join(SERVE_DIR, "ops", "victim.txt"), "delete me");
writeFileSync(join(SERVE_DIR, "ops", "old-name.txt"), "rename me");

const browser = await launchBrowser();
const page = await (await browser.newContext(CONTEXT_OPTIONS)).newPage();
await page.goto(BASE, { waitUntil: "networkidle" });

// Enter the ops/ directory
await page.click(`#server-list a:text("ops/")`);
await page.waitForSelector(`#server-list a:text("victim.txt")`);

let pass = 0;
let fail = 0;
const check = (label, cond) => {
  if (cond) { pass++; console.log(`PASS: ${label}`); }
  else { fail++; console.log(`FAIL: ${label}`); }
};

// Rename old-name.txt -> new-name.txt via the ✎ button's prompt()
page.once("dialog", (d) => d.accept("new-name.txt"));
await page.click(`#server-list li:has(a:text("old-name.txt")) button[title^="Rename"]`);
await page.waitForSelector(`#server-list a:text("new-name.txt")`, { timeout: 15000 });
check("renamed on disk", existsSync(join(SERVE_DIR, "ops", "new-name.txt"))
  && !existsSync(join(SERVE_DIR, "ops", "old-name.txt")));

// Delete victim.txt via the 🗑 button's confirm()
page.once("dialog", (d) => d.accept());
await page.click(`#server-list li:has(a:text("victim.txt")) button[title^="Delete"]`);
await page.waitForFunction(
  () => ![...document.querySelectorAll("#server-list a")].some((a) => a.textContent === "victim.txt"),
  { timeout: 15000 }
);
check("deleted on disk", !existsSync(join(SERVE_DIR, "ops", "victim.txt")));

// Recursive folder delete from the parent listing
await page.click(`#breadcrumbs a:text("files")`);
await page.waitForSelector(`#server-list a:text("ops/")`);
page.once("dialog", (d) => d.accept());
await page.click(`#server-list li:has(a:text("ops/")) button[title^="Delete"]`);
await page.waitForFunction(
  () => ![...document.querySelectorAll("#server-list a")].some((a) => a.textContent === "ops/"),
  { timeout: 15000 }
);
check("folder recursively deleted", !existsSync(join(SERVE_DIR, "ops")));

console.log(fail === 0 ? "TEST PASS" : "TEST FAIL");
await browser.close();
process.exit(fail === 0 ? 0 : 1);
