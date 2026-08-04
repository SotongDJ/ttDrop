/* Verifies the default posture: browser file management is OFF —
 * the listing carries fileOps:false, no rename/delete buttons render,
 * and direct API calls get 403. Spawns its own default-config server. */
import { launchBrowser, CONTEXT_OPTIONS } from "./lib.mjs";
import { spawn } from "node:child_process";
import { writeFileSync, mkdtempSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { tmpdir } from "node:os";

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const jar = join(repoRoot, "dist", "ttdrop.jar");
const pixiJava = join(repoRoot, ".pixi", "envs", "default", "bin", "java");
const java = process.env.JAVA || (existsSync(pixiJava) ? pixiJava : "java");

const serveDir = mkdtempSync(join(tmpdir(), "ttdrop-nofileops-"));
writeFileSync(join(serveDir, "hands-off.txt"), "protected");

const server = spawn(java, ["-jar", jar, "--headless", "--http", "--port", "0", "--open"],
  { cwd: serveDir });
const port = await new Promise((resolve, reject) => {
  let buf = "";
  const timer = setTimeout(() => reject(new Error("server did not start")), 15000);
  server.stdout.on("data", (d) => {
    buf += d;
    const m = buf.match(/on port (\d+)/);
    if (m) {
      clearTimeout(timer);
      resolve(m[1]);
    }
  });
});

let pass = 0;
let fail = 0;
const check = (label, cond) => {
  if (cond) { pass++; console.log(`PASS: ${label}`); }
  else { fail++; console.log(`FAIL: ${label}`); }
};

try {
  const listing = await (await fetch(`http://localhost:${port}/files/`)).json();
  check("listing reports fileOps:false", listing.fileOps === false);

  const del = await fetch(`http://localhost:${port}/api/files/delete?path=hands-off.txt`, { method: "POST" });
  check("delete rejected with 403", del.status === 403);
  check("file untouched", existsSync(join(serveDir, "hands-off.txt")));

  const ren = await fetch(
    `http://localhost:${port}/api/files/rename?path=hands-off.txt&to=renamed.txt`, { method: "POST" });
  check("rename rejected with 403", ren.status === 403);

  const browser = await launchBrowser();
  const page = await (await browser.newContext(CONTEXT_OPTIONS)).newPage();
  await page.goto(`http://localhost:${port}/`, { waitUntil: "networkidle" });
  // Download buttons are read-only and always allowed; only the
  // privileged rename/delete buttons must disappear.
  const buttons = await page.evaluate(() =>
    document.querySelectorAll(
      '#server-list button[title^="Rename"], #server-list button[title^="Delete"]').length);
  check("no rename/delete buttons rendered", buttons === 0);
  await browser.close();
} finally {
  server.kill();
}

console.log(fail === 0 ? "TEST PASS" : "TEST FAIL");
process.exit(fail === 0 ? 0 : 1);
