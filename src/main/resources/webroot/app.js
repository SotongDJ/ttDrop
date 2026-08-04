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

function queueFiles(files) {
  for (const file of files) {
    const li = el("li");
    li.append(el("span", "name", file.name), el("span", "size", formatSize(file.size)));
    const status = el("span", "muted", "queued");
    li.append(status);
    sendList.append(li);
    // Transfer engine (chunked, resumable, OPFS-staged) arrives in the
    // next batch; for now selections are only queued visually.
  }
}

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
