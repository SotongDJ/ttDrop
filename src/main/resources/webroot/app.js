"use strict";

const connStatus = document.getElementById("conn-status");
const dropZone = document.getElementById("drop-zone");
const fileInput = document.getElementById("file-input");
const sendList = document.getElementById("send-list");
const receiveList = document.getElementById("receive-list");
const serverList = document.getElementById("server-list");
const breadcrumbs = document.getElementById("breadcrumbs");

let currentDir = "";

if ("serviceWorker" in navigator && window.isSecureContext) {
navigator.serviceWorker.register("/sw.js").catch(() => {
});
}

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

const folderButton = document.getElementById("folder-button");
const folderInput = document.getElementById("folder-input");

dropZone.addEventListener("click", (e) => {
if (e.target !== folderButton) fileInput.click();
});
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
folderButton.addEventListener("click", () => folderInput.click());
folderInput.addEventListener("change", () => {
queueFiles(folderInput.files);
folderInput.value = "";
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
dropZone.addEventListener("drop", (e) => {
const items = e.dataTransfer.items;
if (items && items.length && items[0].webkitGetAsEntry) {
collectDropped(items).then((entries) => queueEntries(entries));
} else {
queueFiles(e.dataTransfer.files);
}
});

async function collectDropped(items) {
const out = [];
const walk = async (entry, prefix) => {
if (entry.isFile) {
const file = await new Promise((res, rej) => entry.file(res, rej));
out.push({ file, relPath: prefix + file.name });
} else if (entry.isDirectory) {
const reader = entry.createReader();
for (;;) {
const batch = await new Promise((res, rej) => reader.readEntries(res, rej));
if (!batch.length) break;
for (const child of batch) {
await walk(child, prefix + entry.name + "/");
}
}
}
};
const entries = [];
for (const item of items) {
const entry = item.webkitGetAsEntry();
if (entry) entries.push(entry);
}
for (const entry of entries) {
await walk(entry, "");
}
return out;
}

const CHUNK_SIZE = 4 * 1024 * 1024;
const CONCURRENCY = 3;
const transfers = new Map();

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

async function removeStaging(dirName, key) {
if (!opfsAvailable()) return;
const root = await navigator.storage.getDirectory();
let dir;
try {
dir = await root.getDirectoryHandle(dirName);
} catch {
return;
}
for (const entry of [`${key}.bin`, `${key}.json`]) {
for (let attempt = 0; attempt < 5; attempt++) {
try {
await dir.removeEntry(entry);
break;
} catch (err) {
if (err && err.name === "NotFoundError") break;
await new Promise((r) => setTimeout(r, 200));
}
}
}
}

function addCancelButton(li, onCancel) {
const cancel = el("button", "cancel", "✕");
cancel.type = "button";
cancel.title = "Cancel transfer";
cancel.onclick = onCancel;
li.append(cancel);
return cancel;
}

function addTransferRow(key, name, size) {
const li = el("li");
const progress = document.createElement("progress");
progress.max = 1;
progress.value = 0;
const status = el("span", "muted", "starting");
li.append(el("span", "name", name), el("span", "size", formatSize(size)), progress, status);
const row = { progress, status, li, worker: null };
row.cancel = addCancelButton(li, () => {
if (row.worker) row.worker.terminate();
row.status.textContent = "cancelled";
row.status.className = "muted";
row.progress.remove();
row.cancel.remove();
fetch(`/api/upload/abort?key=${key}`, { method: "POST" }).catch(() => {});
removeStaging("ttdrop-outgoing", key);
transfers.delete(key);
});
sendList.append(li);
transfers.set(key, row);
}

function spawnWorker(message) {
const worker = new Worker("/uploader.js");
const row = transfers.get(message.key);
if (row) row.worker = worker;
worker.onmessage = (e) => {
const msg = e.data;
const current = transfers.get(msg.key);
if (!current) return;
if (msg.type === "progress") {
current.progress.value = msg.total ? msg.done / msg.total : 1;
current.status.textContent = `${Math.round(100 * current.progress.value)}%`;
} else if (msg.type === "done") {
current.progress.value = 1;
current.status.textContent = `sent as ${msg.name}`;
current.status.className = "status-ok";
current.cancel.remove();
worker.terminate();
loadDir(currentDir).catch(showOffline);
} else if (msg.type === "error") {
current.status.textContent = msg.message;
current.status.className = "status-err";
worker.terminate();
}
};
worker.postMessage(message);
}

function queueFiles(files) {
queueEntries(Array.from(files, (file) => ({
file,
relPath: file.webkitRelativePath || file.name,
})));
}

function queueEntries(entries) {
for (const { file, relPath } of entries) {
const key = transferKey(relPath, file.size, file.lastModified);
if (transfers.has(key)) continue;
addTransferRow(key, relPath, file.size);
spawnWorker(
{ cmd: "upload", key, file, relPath, chunkSize: CHUNK_SIZE, concurrency: CONCURRENCY });
}
}

const downloads = new Map();

function opfsAvailable() {
return !!(navigator.storage && navigator.storage.getDirectory);
}

function addDownloadRow(key, name, size) {
const li = el("li");
const progress = document.createElement("progress");
progress.max = 1;
progress.value = 0;
const status = el("span", "muted", "starting");
li.append(el("span", "name", name), el("span", "size", formatSize(size)), progress, status);
const row = { progress, status, li, worker: null };
row.cancel = addCancelButton(li, () => {
if (row.worker) row.worker.terminate();
row.status.textContent = "cancelled";
row.status.className = "muted";
row.progress.remove();
row.cancel.remove();
removeStaging("ttdrop-incoming", key);
downloads.delete(key);
});
receiveList.append(li);
downloads.set(key, row);
}

async function deliverDownload(key, name) {
const root = await navigator.storage.getDirectory();
const dir = await root.getDirectoryHandle("ttdrop-incoming");
const file = await (await dir.getFileHandle(`${key}.bin`)).getFile();
const url = URL.createObjectURL(file);
const a = document.createElement("a");
a.href = url;
a.download = name;
a.click();
const worker = new Worker("/downloader.js");
worker.onmessage = () => worker.terminate();
worker.postMessage({ cmd: "markDelivered", key });
}

function spawnDownloadWorker(message) {
const worker = new Worker("/downloader.js");
const row = downloads.get(message.key);
if (row) row.worker = worker;
worker.onmessage = (e) => {
const msg = e.data;
const current = downloads.get(msg.key);
if (!current) return;
if (msg.type === "progress") {
current.progress.value = msg.total ? msg.done / msg.total : 1;
current.status.textContent = `${Math.round(100 * current.progress.value)}%`;
} else if (msg.type === "done") {
current.progress.value = 1;
current.cancel.remove();
worker.terminate();
deliverDownload(msg.key, msg.name)
.then(() => {
current.status.textContent = "saved";
current.status.className = "status-ok";
})
.catch((err) => {
current.status.textContent = String(err.message || err);
current.status.className = "status-err";
});
} else if (msg.type === "error") {
current.status.textContent = msg.message;
current.status.className = "status-err";
worker.terminate();
}
};
worker.postMessage(message);
}

async function startDownload(path, name, size) {
if (!opfsAvailable()) {
window.location.href = path;
return;
}
const head = await fetch(path, { method: "HEAD" });
if (!head.ok) {
showOffline();
return;
}
const etag = head.headers.get("ETag");
const key = transferKey(path, size, etag || "");
if (downloads.has(key)) return;
addDownloadRow(key, name, size);
spawnDownloadWorker({
cmd: "download", key, path, name, size, etag,
chunkSize: CHUNK_SIZE, concurrency: CONCURRENCY,
});
}

async function resumePending() {
if (!opfsAvailable()) return;
const root = await navigator.storage.getDirectory();
for (const [dirName, isUpload] of [["ttdrop-outgoing", true], ["ttdrop-incoming", false]]) {
try {
const dir = await root.getDirectoryHandle(dirName);
for await (const [entryName, handle] of dir.entries()) {
if (!entryName.endsWith(".json")) continue;
const meta = JSON.parse(await (await handle.getFile()).text());
if (isUpload) {
if (transfers.has(meta.key)) continue;
addTransferRow(meta.key, meta.name, meta.size);
spawnWorker({ cmd: "resume", key: meta.key, concurrency: CONCURRENCY });
} else if (meta.delivered) {
await dir.removeEntry(`${meta.key}.bin`).catch(() => {});
await dir.removeEntry(`${meta.key}.json`).catch(() => {});
} else {
if (downloads.has(meta.key)) continue;
addDownloadRow(meta.key, meta.name, meta.size);
spawnDownloadWorker({ cmd: "resume", key: meta.key, concurrency: CONCURRENCY });
}
}
} catch {
}
}
}

resumePending();

const qrShare = document.getElementById("qr-share");
qrShare.addEventListener("toggle", () => {
const img = document.getElementById("qr-img");
if (qrShare.open && !img.src) {
img.src = "/qr.png";
}
});

async function loadDir(path) {
const res = await fetch(`/files/${path}`, { headers: { Accept: "application/json" } });
if (!res.ok) throw new Error(`listing failed: ${res.status}`);
const data = await res.json();
currentDir = path;
renderBreadcrumbs(path);
renderListing(data.entries, path, data.fileOps === true);
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

const ICON_DIR =
'<svg aria-hidden="true" viewBox="0 0 16 16" width="16" height="16" fill="current' +
'Color"><path d="M1.75 1A1.75 1.75 0 0 0 0 2.75v10.5C0 14.216.784 15 1.75 15h12.5' +
'A1.75 1.75 0 0 0 16 13.25v-8.5A1.75 1.75 0 0 0 14.25 3H7.5a.25.25 0 0 1-.2-.1l-.' +
'9-1.2C6.07 1.26 5.55 1 5 1H1.75Z"/></svg>';
const ICON_FILE =
'<svg aria-hidden="true" viewBox="0 0 16 16" width="16" height="16" fill="current' +
'Color"><path d="M2 1.75C2 .784 2.784 0 3.75 0h6.586c.464 0 .909.184 1.237.513l2.' +
'914 2.914c.329.328.513.773.513 1.237v9.586A1.75 1.75 0 0 1 13.25 16h-9.5A1.75 1.' +
'75 0 0 1 2 14.25Zm1.75-.25a.25.25 0 0 0-.25.25v12.5c0 .138.112.25.25.25h9.5a.25.' +
'25 0 0 0 .25-.25V6h-2.75A1.75 1.75 0 0 1 9 4.25V1.5Zm6.75.062V4.25c0 .138.112.25' +
'.25.25h2.688l-.011-.013-2.914-2.914-.013-.011Z"/></svg>';

function iconSpan(dir) {
const span = el("span", dir ? "icon dir" : "icon");
span.innerHTML = dir ? ICON_DIR : ICON_FILE;
return span;
}

function formatAgo(mtime) {
const s = Math.max(0, (Date.now() - mtime) / 1000);
if (s < 45) return "just now";
const steps = [[60, "minute"], [60, "hour"], [24, "day"], [30, "month"], [12, "year"]];
let value = s;
let unit = "second";
for (const [div, next] of steps) {
if (value < div) break;
value /= div;
unit = next;
}
const n = Math.max(1, Math.floor(value));
return `${n} ${unit}${n === 1 ? "" : "s"} ago`;
}

function renderListing(entries, path, fileOps) {
serverList.textContent = "";
if (!entries.length) {
serverList.append(el("li", "muted", "Empty folder"));
return;
}
const sorted = [...entries].sort((a, b) => (b.dir - a.dir) || a.name.localeCompare(b.name));
for (const entry of sorted) {
const li = el("li");
li.append(iconSpan(entry.dir));
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
const filePath = `/files/${path}${encodeURIComponent(entry.name)}`;
const link = el("a", null, entry.name);
link.href = filePath;
link.onclick = (e) => {
e.preventDefault();
startDownload(filePath, entry.name, entry.size);
};
name.append(link);
}
li.append(name);
if (!entry.dir) li.append(el("span", "size", formatSize(entry.size)));
if (entry.mtime) li.append(el("span", "age", formatAgo(entry.mtime)));
if (entry.dir) {
li.append(fileOpButton("⬇", `Download ${entry.name} as zip`, () => {
window.location.href = `/api/zip?path=${encodeURIComponent(path + entry.name)}`;
}));
}
if (fileOps) {
li.append(fileOpButton("✎", `Rename ${entry.name}`, () => renameEntry(path, entry)));
li.append(fileOpButton("🗑", `Delete ${entry.name}`, () => deleteEntry(path, entry)));
}
serverList.append(li);
}
}

function fileOpButton(label, title, onClick) {
const button = el("button", "file-op", label);
button.type = "button";
button.title = title;
button.onclick = onClick;
return button;
}

async function renameEntry(path, entry) {
const to = window.prompt(`Rename "${entry.name}" to:`, entry.name);
if (!to || to === entry.name) return;
const res = await fetch(
`/api/files/rename?path=${encodeURIComponent(path + entry.name)}&to=${encodeURIComponent(to)}`,
{ method: "POST" });
if (!res.ok) {
const body = await res.json().catch(() => ({}));
window.alert(`Rename failed: ${body.error || res.status}`);
}
loadDir(currentDir).catch(showOffline);
}

async function deleteEntry(path, entry) {
const what = entry.dir ? `folder "${entry.name}" and everything in it` : `"${entry.name}"`;
if (!window.confirm(`Delete ${what}?`)) return;
const res = await fetch(
`/api/files/delete?path=${encodeURIComponent(path + entry.name)}`,
{ method: "POST" });
if (!res.ok && res.status !== 404) {
window.alert(`Delete failed: ${res.status}`);
}
loadDir(currentDir).catch(showOffline);
}

function showOffline() {
connStatus.textContent = "Server unreachable";
connStatus.classList.add("status-err");
}

const pairSection = document.getElementById("pair-section");
const sendSection = document.getElementById("send-section");
const receiveSection = document.getElementById("receive-section");
const browseSection = document.getElementById("browse-section");

async function pairWith(code, name) {
const res = await fetch(
`/api/pair?code=${encodeURIComponent(code)}&name=${encodeURIComponent(name)}`,
{ method: "POST" });
const body = await res.json().catch(() => ({}));
if (!res.ok) throw new Error(body.error || `pair failed: ${res.status}`);
return body;
}

async function initSession() {
try {
const params = new URLSearchParams(location.search);
if (params.has("pair")) {
document.getElementById("pair-code").value = params.get("pair");
history.replaceState(null, "", location.pathname);
}
const session = await (await fetch("/api/session")).json();
if (session.pairingRequired && !session.paired) {
pairSection.hidden = false;
sendSection.hidden = true;
receiveSection.hidden = true;
browseSection.hidden = true;
connStatus.textContent = "Not paired";
document.getElementById("pair-name").focus();
return;
}
pairSection.hidden = true;
sendSection.hidden = session.write === false;
receiveSection.hidden = session.read === false;
browseSection.hidden = session.read === false;
connStatus.textContent = session.name ? `Connected — paired as ${session.name}` : "Connected";
if (session.read !== false) await loadDir("");
} catch {
showOffline();
}
}

document.getElementById("pair-form").addEventListener("submit", async (e) => {
e.preventDefault();
const errEl = document.getElementById("pair-error");
errEl.hidden = true;
try {
await pairWith(
document.getElementById("pair-code").value,
document.getElementById("pair-name").value.trim());
await initSession();
} catch (err) {
errEl.textContent = `Pairing failed: ${err.message}`;
errEl.hidden = false;
}
});

initSession();
