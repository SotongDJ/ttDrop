/* ttDrop download worker: chunked, parallel, resumable via OPFS staging.
 *
 * Messages in:
 *   {cmd:"download", key, path, name, size, etag, chunkSize, concurrency}
 *   {cmd:"resume", key, concurrency} — continue from OPFS staging.
 * Messages out:
 *   {type:"progress", key, done, total} | {type:"done", key, name}
 *   | {type:"error", key, message}
 *
 * Chunks are fetched with Range requests and written at their offsets
 * into an OPFS staging file; completed-chunk indexes are tracked in the
 * meta JSON after each write, so a reload resumes cleanly. The staged
 * ETag detects server-side changes and restarts the transfer. When done,
 * the main thread reads the staged file and hands it to the user.
 */
"use strict";

const OPFS_DIR = "ttdrop-incoming";

self.onmessage = async (e) => {
  const msg = e.data;
  try {
    if (msg.cmd === "download") {
      await download(msg);
    } else if (msg.cmd === "resume") {
      await resume(msg);
    } else if (msg.cmd === "markDelivered") {
      await markDelivered(msg.key);
    }
  } catch (err) {
    self.postMessage({ type: "error", key: msg.key, message: String(err && err.message || err) });
  }
};

/* Flag a finished transfer as handed to the browser so the next page
 * load can delete the staging (deleting immediately would cancel the
 * in-flight save, which streams lazily from the OPFS file). */
async function markDelivered(key) {
  const dir = await opfsDir();
  const metaHandle = await dir.getFileHandle(`${key}.json`);
  const meta = JSON.parse(await (await metaHandle.getFile()).text());
  meta.delivered = true;
  await writeMeta(dir, key, meta);
  self.postMessage({ type: "markedDelivered", key });
}

async function opfsDir() {
  const root = await navigator.storage.getDirectory();
  return root.getDirectoryHandle(OPFS_DIR, { create: true });
}

async function writeMeta(dir, key, meta) {
  const handle = await dir.getFileHandle(`${key}.json`, { create: true });
  const access = await handle.createSyncAccessHandle();
  try {
    access.truncate(0);
    access.write(new TextEncoder().encode(JSON.stringify(meta)), { at: 0 });
    access.flush();
  } finally {
    access.close();
  }
}

async function download({ key, path, name, size, etag, chunkSize, concurrency }) {
  const dir = await opfsDir();
  const meta = { key, path, name, size, etag, chunkSize, have: [] };
  await writeMeta(dir, key, meta);
  await transfer(dir, meta, concurrency);
}

async function resume({ key, concurrency }) {
  const dir = await opfsDir();
  const metaHandle = await dir.getFileHandle(`${key}.json`);
  const meta = JSON.parse(await (await metaHandle.getFile()).text());

  // If the file changed on the server, staged chunks are stale: start over.
  const head = await fetch(meta.path, { method: "HEAD" });
  if (!head.ok) throw new Error(`file gone (${head.status})`);
  if (head.headers.get("ETag") !== meta.etag) {
    meta.etag = head.headers.get("ETag");
    meta.size = Number(head.headers.get("Content-Length"));
    meta.have = [];
    await writeMeta(dir, meta.key, meta);
  }
  await transfer(dir, meta, concurrency);
}

async function transfer(dir, meta, concurrency = 3) {
  const { key, path, size, chunkSize } = meta;
  const chunkCount = size === 0 ? 1 : Math.ceil(size / chunkSize);
  const haveSet = new Set(meta.have);
  const pending = [];
  for (let i = 0; i < chunkCount; i++) {
    if (!haveSet.has(i)) pending.push(i);
  }
  let done = chunkCount - pending.length;
  self.postMessage({ type: "progress", key, done, total: chunkCount });

  const binHandle = await dir.getFileHandle(`${key}.bin`, { create: true });
  const access = await binHandle.createSyncAccessHandle();
  try {
    let next = 0;
    const pump = async () => {
      while (next < pending.length) {
        const index = pending[next++];
        const start = index * chunkSize;
        const end = Math.min(size, start + chunkSize) - 1;
        const bytes = await getRangeWithRetry(path, start, end);
        access.write(new Uint8Array(bytes), { at: start });
        access.flush();
        haveSet.add(index);
        meta.have = [...haveSet];
        await writeMeta(dir, key, meta);
        done++;
        self.postMessage({ type: "progress", key, done, total: chunkCount });
      }
    };
    await Promise.all(Array.from({ length: Math.min(concurrency, Math.max(pending.length, 1)) }, pump));
  } finally {
    access.close();
  }
  self.postMessage({ type: "done", key, name: meta.name });
}

async function getRangeWithRetry(path, start, end, attempts = 4) {
  let delay = 500;
  for (let i = 1; ; i++) {
    try {
      const res = await fetch(path, { headers: { Range: `bytes=${start}-${end}` } });
      if (res.status === 206 || (res.status === 200 && start === 0)) {
        const buf = await res.arrayBuffer();
        if (buf.byteLength === end - start + 1) return buf;
        if (res.status === 200) return buf.slice(start, end + 1);
        throw new Error("short range response");
      }
      if (res.status >= 400 && res.status < 500) {
        throw new Error(`range rejected (${res.status})`);
      }
    } catch (err) {
      if (i >= attempts) throw err;
    }
    if (i >= attempts) throw new Error("chunk download failed after retries");
    await new Promise((r) => setTimeout(r, delay));
    delay *= 2;
  }
}
