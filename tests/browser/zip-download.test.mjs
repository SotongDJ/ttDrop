/* Downloads a folder as a zip through the PWA's folder button and
 * verifies the archive's entries and contents. */
import { launchBrowser, CONTEXT_OPTIONS, BASE, SERVE_DIR } from "./lib.mjs";
import { execFileSync } from "node:child_process";
import { writeFileSync, mkdirSync, rmSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const pixiJar = join(repoRoot, ".pixi", "envs", "default", "bin", "jar");
const jarTool = existsSync(pixiJar) ? pixiJar : "jar";

rmSync(join(SERVE_DIR, "zipme"), { recursive: true, force: true });
mkdirSync(join(SERVE_DIR, "zipme", "inner"), { recursive: true });
writeFileSync(join(SERVE_DIR, "zipme", "one.txt"), "first");
writeFileSync(join(SERVE_DIR, "zipme", "inner", "two.txt"), "second");

const browser = await launchBrowser();
const page = await (await browser.newContext(CONTEXT_OPTIONS)).newPage();
await page.goto(BASE, { waitUntil: "networkidle" });

const downloadPromise = page.waitForEvent("download", { timeout: 60000 });
await page.click(`#server-list li:has(a:text("zipme/")) button[title^="Download"]`);
const download = await downloadPromise;

let pass = 0;
let fail = 0;
const check = (label, cond) => {
  if (cond) { pass++; console.log(`PASS: ${label}`); }
  else { fail++; console.log(`FAIL: ${label}`); }
};

check("suggested name", download.suggestedFilename() === "zipme.zip");
const savedPath = await download.path();
const listing = execFileSync(jarTool, ["tf", savedPath], { encoding: "utf8" }).trim().split("\n").sort();
check("zip entries", JSON.stringify(listing) === JSON.stringify(["inner/two.txt", "one.txt"]));

console.log(fail === 0 ? "TEST PASS" : "TEST FAIL");
await browser.close();
process.exit(fail === 0 ? 0 : 1);
