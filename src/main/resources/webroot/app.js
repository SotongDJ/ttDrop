/* ttDrop PWA — vanilla JS, browser-native APIs only. */
"use strict";

const connStatus = document.getElementById("conn-status");
const dropZone = document.getElementById("drop-zone");
const fileInput = document.getElementById("file-input");
const sendList = document.getElementById("send-list");
const serverList = document.getElementById("server-list");
const breadcrumbs = document.getElementById("breadcrumbs");

let currentDir = "";

/* ---------- service worker (secure contexts only) ---------- */

if ("serviceWorker" in navigator && window.isSecureContext) {
  navigator.serviceWorker.register("/sw.js").catch(() => {
    /* PWA install unavailable; the page still works. */
  });
}

/* ---------- helpers ---------- */

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let v = bytes;
  let u = -1;
  do {
    v /= 1024;
    u++;
  } while (v >= 1024 && u < units.length - 1);
  return `${v.toFixed(v < 10 ? 1 : 0)} ${units[u]}`;
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

/* ---------- file selection ---------- */

dropZone.addEventListener("click", () => fileInput.click());
dropZone.addEventListener("keydown", (e) => {
  if (e.key === "Enter" || e.key === " ") {
    e.preventDefault();
    fileInput.click();
  }
});
fileInput.addEventListener("change", () => {
  queueFiles(fileInput.files);
  fileInput.value = "";
});

["dragenter", "dragover"].forEach((type) =>
  dropZone.addEventListener(type, (e) => {
    e.preventDefault();
    dropZone.classList.add("dragover");
  })
);
["dragleave", "drop"].forEach((type) =>
  dropZone.addEventListener(type, (e) => {
    e.preventDefault();
    dropZone.classList.remove("dragover");
  })
);
dropZone.addEventListener("drop", (e) => queueFiles(e.dataTransfer.files));

/* ---------- transfer engine ---------- */

const CHUNK_SIZE = 4 * 1024 * 1024;
const CONCURRENCY = 3;
const transfers = new Map();

/* Stable hex key from file identity — resumes match across reloads.
 * FNV-1a with two seeds; an identifier, not a security digest, so it
 * works in insecure LAN contexts where crypto.subtle is unavailable. */
function transferKey(name, size, lastModified) {
  const input = `${name}|${size}|${lastModified}`;
  let out = "";
  for (const seed of [0x811c9dc5, 0x01000193 ^ 0x5bd1e995]) {
    let h = seed >>> 0;
    for (let i = 0; i < input.length; i++) {
      h ^= input.charCodeAt(i);
      h = Math.imul(h, 0x01000193) >>> 0;
    }
    out += h.toString(16).padStart(8, "0");
  }
  return out;
}

function addTransferRow(key, name, size) {
  const li = el("li");
  const progress = document.createElement("progress");
  progress.max = 1;
  progress.value = 0;
  const status = el("span", "muted", "starting");
  li.append(el("span", "name", name), el("span", "size", formatSize(size)), progress, status);
  sendList.append(li);
  transfers.set(key, { progress, status, li });
}

function spawnWorker(message) {
  const worker = new Worker("/uploader.js");
  worker.onmessage = (e) => {
    const msg = e.data;
    const row = transfers.get(msg.key);
    if (!row) return;
    if (msg.type === "progress") {
      row.progress.value = msg.total ? msg.done / msg.total : 1;
      row.status.textContent = `${Math.round(100 * row.progress.value)}%`;
    } else if (msg.type === "done") {
      row.progress.value = 1;
      row.status.textContent = `sent as ${msg.name}`;
      row.status.className = "status-ok";
      worker.terminate();
      loadDir(currentDir).catch(showOffline);
    } else if (msg.type === "error") {
      row.status.textContent = msg.message;
      row.status.className = "status-err";
      worker.terminate();
    }
  };
  worker.postMessage(message);
}

function queueFiles(files) {
  for (const file of files) {
    const key = transferKey(file.name, file.size, file.lastModified);
    if (transfers.has(key)) continue;
    addTransferRow(key, file.name, file.size);
    spawnWorker({ cmd: "upload", key, file, chunkSize: CHUNK_SIZE, concurrency: CONCURRENCY });
  }
}

/* Resume transfers whose bytes are still staged in OPFS from a previous
 * page load. Silently does nothing where OPFS is unavailable. */
async function resumePending() {
  if (!navigator.storage || !navigator.storage.getDirectory) return;
  try {
    const root = await navigator.storage.getDirectory();
    const dir = await root.getDirectoryHandle("ttdrop-outgoing");
    for await (const [entryName, handle] of dir.entries()) {
      if (!entryName.endsWith(".json")) continue;
      const meta = JSON.parse(await (await handle.getFile()).text());
      if (transfers.has(meta.key)) continue;
      addTransferRow(meta.key, meta.name, meta.size);
      spawnWorker({ cmd: "resume", key: meta.key, concurrency: CONCURRENCY });
    }
  } catch {
    /* no staging directory yet — nothing to resume */
  }
}

resumePending();

/* ---------- server file browser ---------- */

async function loadDir(path) {
  const res = await fetch(`/files/${path}`, { headers: { Accept: "application/json" } });
  if (!res.ok) throw new Error(`listing failed: ${res.status}`);
  const data = await res.json();
  currentDir = path;
  renderBreadcrumbs(path);
  renderListing(data.entries, path);
}

function renderBreadcrumbs(path) {
  breadcrumbs.textContent = "";
  const rootLink = el("a", null, "files");
  rootLink.href = "#";
  rootLink.onclick = (e) => {
    e.preventDefault();
    loadDir("").catch(showOffline);
  };
  breadcrumbs.append(rootLink);
  let acc = "";
  for (const part of path.split("/").filter(Boolean)) {
    acc += `${part}/`;
    breadcrumbs.append(" / ");
    const link = el("a", null, part);
    const target = acc;
    link.href = "#";
    link.onclick = (e) => {
      e.preventDefault();
      loadDir(target).catch(showOffline);
    };
    breadcrumbs.append(link);
  }
}

function renderListing(entries, path) {
  serverList.textContent = "";
  if (!entries.length) {
    serverList.append(el("li", "muted", "Empty folder"));
    return;
  }
  for (const entry of entries) {
    const li = el("li");
    const name = el("span", "name");
    if (entry.dir) {
      const link = el("a", null, `${entry.name}/`);
      link.href = "#";
      link.onclick = (e) => {
        e.preventDefault();
        loadDir(`${path}${entry.name}/`).catch(showOffline);
      };
      name.append(link);
    } else {
      const link = el("a", null, entry.name);
      link.href = `/files/${path}${encodeURIComponent(entry.name)}`;
      name.append(link);
    }
    li.append(name);
    if (!entry.dir) li.append(el("span", "size", formatSize(entry.size)));
    serverList.append(li);
  }
}

function showOffline() {
  connStatus.textContent = "Server unreachable";
  connStatus.classList.add("status-err");
}

loadDir("")
  .then(() => {
    connStatus.textContent = "Connected";
  })
  .catch(showOffline);
