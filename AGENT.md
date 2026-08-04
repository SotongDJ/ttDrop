# AGENT.md — Guide for LLM Agents

This file is the entry point for any LLM agent (Claude, Copilot, Cursor, etc.)
working on this repository. Read it fully before making changes, and keep it
up to date as the project evolves.

## What ttDrop is

**ttDrop** is a local file-transfer tool with two halves:

1. **Server app (Java)** — a desktop application for Windows, macOS, and
   Linux. When started, it acts as:
   - the **PWA host** — serves the sender web app to devices on the network;
   - the **HTTP server** — handles the transfer endpoints;
   - the **receiver host** — receives files sent from the PWA and makes
     files available for download.
2. **PWA (browser)** — a Progressive Web App opened on any device
   (PC, Mac, iPhone, iPad, Android) that acts as the **file sender and
   downloader**, talking to the server over local HTTP.

Typical flow: a user starts the server app on their computer, opens the
served PWA on another device on the same network, and sends files to (or
downloads files from) the server.

### File transfer design

When the user selects files, the PWA **processes them for multithreaded,
continue-able (resumable) transfer** between the PWA and the server. This
is a core design requirement, in both directions (upload and download):

- Files are split into **chunks** so multiple chunks can be transferred
  **concurrently** (parallel requests / workers), not as one monolithic
  stream.
- Transfers must be **resumable**: after an interruption (closed tab,
  dropped Wi-Fi, server restart), a transfer continues from the chunks
  already completed instead of starting over. This implies both sides
  track per-chunk completion state, and chunk integrity is verifiable.
- On the PWA side this must be built from browser-native primitives only
  (e.g. `Blob.slice`, `fetch`, Web Workers, streams) — see the constraints
  below. Chunk size and concurrency should account for memory limits on
  mobile Safari/Android browsers.
- The PWA uses **OPFS (Origin Private File System)** for temporary
  handling: staging chunks, partially downloaded files, and resume state
  live in OPFS rather than in memory, so large files survive page reloads
  and don't exhaust RAM. Prefer `createSyncAccessHandle()` inside Web
  Workers for chunk I/O (the widely supported OPFS write path, including
  Safari); clean up OPFS temporaries once a transfer completes.
- **Upload protocol** (implemented; keep PWA and server in lockstep):
  - `POST /api/upload/init?key=&path=&size=&chunkSize=` → creates or
    finds the staging area; returns `{"key","chunkCount","have":[...]}`
    where `have` lists chunk indexes already stored (resume). `path` is
    a forward-slash relative path for folder uploads (`name` is the
    legacy flat-file spelling); every segment is sanitized, traversal
    rejected, depth capped at 32.
  - `PUT /api/upload/chunk?key=&index=n` (raw body) → stores one chunk;
    written to a temp file then atomically renamed, so parallel chunk
    uploads and crashes are safe. Exact-size check per chunk.
  - `GET /api/upload/status?key=` → same shape as init's response.
  - `POST /api/upload/abort?key=` → cancels a transfer by dropping its
    staging area (idempotent 204).
  - `POST /api/upload/complete?key=` → verifies all chunks, assembles
    into the file root under a collision-safe name (`name (n).ext`),
    deletes staging; returns `{"name":finalName}`. 409 + missing index
    if incomplete.
  - The `key` is a client-derived stable identifier (lowercase hex,
    8–64 chars) hashed from `name|size|lastModified` — it makes resume
    match across page reloads. It is an identifier, not a security
    digest (crypto.subtle is unavailable in insecure LAN contexts).
  - Server staging lives in `<fileRoot>/.ttdrop-part/<key>/` (hidden
    from `/files/` listings) so partial transfers survive server
    restarts and final assembly is an atomic same-filesystem move.
  - PWA side: `uploader.js` Web Worker stages the file into OPFS
    (`ttdrop-outgoing/<key>.bin` + `.json`, sync access handles), then
    uploads missing chunks with a small parallel pool (default 3 × 4 MiB)
    and retry/backoff; `app.js` `resumePending()` rescans OPFS on load
    and resumes unfinished transfers. Where OPFS is unavailable the
    worker falls back to slicing the original `File` (no reload-resume).
  - File names are sanitized server-side (separators/reserved characters
    stripped, no traversal, 255-char cap).
- **QR endpoint** (implemented): `GET /qr.png` returns a QR PNG of
  `http://<Host header>/` (the URL the requesting client used), with a
  `?text=` override capped at 80 chars. Backed by `ttdrop.util.QrCode`,
  a pure-JDK encoder (byte mode, versions 1–5, ECC level M, all eight
  masks) — do not swap it for a library; verify any change with
  `tests/qr/`. The Swing GUI shows the same QR for its address picker.
- **File management** (implemented): `POST /api/files/delete?path=`
  (file, or directory recursively) and
  `POST /api/files/rename?path=&to=` (single sanitized name within the
  same directory, 409 on collision). Both resolve strictly inside the
  file root — never the root itself or `.ttdrop-part` — via the upload
  sanitizers (`FileOpsHandler`). **Disabled by default**: requires the
  GUI's "Allow browser file management" toggle (live, persisted) or the
  `--fileops` flag; while off the endpoints return 403 and the `/files/`
  listing advertises `fileOps:false` so the PWA hides the buttons. Keep
  new privileged operations behind this same default-off pattern.
- **Download protocol** (implemented): `/files/<path>` supports `HEAD`
  and single-range `Range: bytes=a-b` GETs (206 + `Content-Range`,
  416 on bad ranges) with an ETag of `"size-mtime"`. The PWA's
  `downloader.js` worker fetches chunks in parallel with Range requests,
  writes them at their offsets into an OPFS staging file
  (`ttdrop-incoming/<key>.bin` + `.json` tracking completed indexes and
  the ETag), and resumes across reloads; an ETag mismatch on resume
  restarts the transfer. When complete, the main thread hands the staged
  file to the browser as a blob-URL save. **Staging must not be deleted
  at delivery time** — the save streams lazily from the OPFS file — so
  the meta is marked `delivered` and cleanup runs on the next page load.
  Without OPFS, file links fall back to plain navigation downloads.

### HTTPS

The server serves **HTTPS by default** (`--http` or the GUI checkbox
opts out; the choice persists in config). `ttdrop.server.TlsSupport`
builds the material with the JDK's `keytool` (resolved from
`java.home`) in `~/.config/ttdrop/`:

- **Per-user CA, generated on the first HTTPS run and reused for all
  sessions**: `ca.p12` (key) + `ca.crt` (PEM export, public half only).
  Served at `GET /ca.crt` and linked from the PWA footer — installing
  it once on a device makes every ttDrop server certificate trusted,
  present and future, which also unlocks service workers and PWA
  install. Never regenerate the CA implicitly except when `ca.crt`/
  `ca.p12` are missing (that would invalidate installed trust).
- **Server certificate**: `keystore.p12`, issued by the CA with SANs
  for `localhost`, `127.0.0.1`, and the LAN IPs present at generation
  time, `eku=serverAuth`, ≤825-day validity (Apple's trust limit).
  Delete `keystore.p12` to re-issue under the same CA (e.g. after an
  IP change) — installed trust persists. A pre-CA `keystore.p12`
  (missing CA files) is discarded and re-issued.
- Fixed keystore password ("ttdrop"): it guards self-signed LAN
  material in the user's own config dir; PKCS12 requires one.

Caveats to preserve in any related change: without installing the CA,
devices tap through the browser interstitial once — a merely-accepted
cert still gives a secure context (OPFS staging and reload-resume
work), but Chromium refuses **service-worker** scripts over it, so the
app must keep registering the SW best-effort and degrading gracefully.
URLs and QR codes must always follow `TtDropServer.scheme()`. The gold
verification for TLS changes: `curl --cacert ~/.config/ttdrop/ca.crt
https://localhost:<port>/` must succeed with no `-k`.

### Server runtime layout

- The server ships as a **GUI jar**. The user **places the jar in the
  target directory and runs it from there** (e.g. `N:\the\target\path` on
  Windows or `/the/target/path` on Unix). That directory is the server's
  **working directory** and is the file root served at
  `https://localhost/files/` — received files land there and files placed
  there are downloadable. Resolve the file root from the actual working
  directory at runtime; never hard-code paths, and handle both Windows
  drive-letter paths and Unix paths.
- File-root override precedence (implemented in `Main`): the
  `--root <dir>` flag, then the persisted GUI chooser selection
  (config `root`), then the working directory. The GUI can change the
  shared folder while stopped and offers a persisted
  "Start on launch" autostart option.
- The **webroot does not exist on disk**. The PWA assets (HTML, JS, CSS,
  manifest, service worker, icons) are embedded in the jar and served by
  the server **on demand** from its resources. Do not scaffold a webroot
  directory next to the jar or read PWA assets from the filesystem.
- **Configuration** lives in `.config/ttdrop/` under the **user home
  directory** (i.e. `~/.config/ttdrop/`, resolved via `user.home` — the
  same layout on Windows, macOS, and Linux). Config never lives in the
  working directory; the working directory is exclusively the file area.
  Implemented as `ttdrop.Config` (`config.properties`; currently stores
  the last used port, saved when the GUI starts the server).
- Keep the served file area and the transfer temporaries distinct from
  config, and make sure serving `/files/` never escapes the working
  directory (path traversal — see the security ground rule).

## Hard constraints — never violate these

### PWA
- **Vanilla JavaScript only.** No external JS libraries, no frameworks, no
  bundler-installed dependencies, no CDN scripts. Not even "small" ones.
- **Vanilla CSS only.** No CSS frameworks (no Tailwind, Bootstrap, etc.),
  no preprocessors as a build requirement.
- **Browser-native APIs only** (Fetch, File/Blob, Service Worker, Web
  Manifest, drag-and-drop, streams, etc.).
- Must work across **desktop browsers, iOS/iPadOS Safari, and Android** —
  check cross-browser support (especially Safari/WebKit, which is the usual
  laggard for File System Access, background sync, and streaming APIs)
  before relying on an API, and provide fallbacks where support is partial.
- It must remain installable/usable as a PWA served from the local Java
  server — no reliance on internet-hosted assets.

### Server
- **Java**, and it must run on Windows, macOS, and Linux. No OS-specific
  behavior without explicit cross-platform handling (paths, file names,
  line endings, default browsers, firewalls/ports).
- The server serves everything the PWA needs; devices on the LAN must not
  need internet access for ttDrop to work.

### General
- The project is licensed **GPL-3.0**. Do not add code copied from
  incompatibly-licensed sources.

If a change you're considering would break one of these constraints, stop
and ask the maintainer instead of proceeding.

## Current state of the repository

Implemented and verified end to end (Playwright/Chromium against the
running jar): the Java server (GUI + headless, embedded webroot,
`/files/` with Range support, chunked upload API with folder paths,
delete/rename API, config persistence, HTTPS-by-default with a
per-user CA, QR endpoints, root chooser/--root/autostart) and the PWA
(installable shell, chunked parallel resumable uploads and downloads
staged in OPFS with reload-resume, folder uploads, cancellation, file
management, QR sharing, CA install link).

Browser tests live in `tests/browser/`, QR tests in `tests/qr/` (see
Testing). Remaining ideas live under Known gaps.

Do not assume the existence of any module, build file, or directory that
is not present on disk — verify with the actual file tree first, and
keep the sections below current in the same commit as any change.

## Ground rules

1. **Never invent project facts.** If something isn't answered by this
   file, the README, the code, or git history, ask the maintainer rather
   than guessing.
2. **Keep this file current.** Any structural change — build system,
   source layout, test framework, CI, release process, new constraint —
   must update the relevant AGENT.md section in the same commit.
3. **Small, reviewable changes.** Focused commits with clear messages.
4. **No secrets, no build artifacts.** Anything matching `.gitignore`
   patterns must never be force-added.
5. **Security matters even on a LAN.** The server accepts file uploads:
   sanitize file names, prevent path traversal, bound sizes sensibly, and
   never execute received content.

## Git workflow

- The default branch is `main`. Never commit directly to `main` unless the
  maintainer explicitly says so.
- Develop on a feature branch (agents in managed environments are usually
  assigned a `claude/...` branch — use the one you were given).
- Push with `git push -u origin <branch-name>`; open a pull request only
  when asked to.
- Commit messages in the imperative mood ("Add drop handler", not "Added
  drop handler"), short subject line, optional body explaining *why*.
- **Every batch updates `CHANGELOG`** (no file extension): add bullets
  describing the batch under the `## Unreleased` section in the same
  commit. CHANGELOG is the single source of truth for GitHub release
  notes — see Releases.
- **Every batch of modifications ends with a commit — nothing more.**
  Never leave finished work uncommitted, but do not push automatically:
  **push and pull requests happen on demand only**, when the maintainer
  asks for them.
  - Exception: in an ephemeral environment (e.g. a Claude Code cloud
    session, where the container and its commits are discarded after the
    session), push the working branch before the session ends so the
    work is not lost. This exception covers branch pushes only — never
    tags, and never opening a PR unasked.
- **Tags are not created during development batches.** They are
  generated on the maintainer's local device: after cloning or pulling
  the repo there, create annotated tags from the commit history (batch
  and milestone commits) and push them to origin from that device.
- **Tag convention:** annotated semver tags `vMAJOR.MINOR.PATCH`
  (e.g. `v0.1.0`). Bump PATCH for an ordinary batch, MINOR for a
  feature milestone, MAJOR for breaking/protocol changes. Keep the
  `version` in `pixi.toml` in sync with the latest tag.

## Environment and build (pixi)

The toolchain is managed with **[pixi](https://pixi.sh)** — do not install
JDKs or build tools any other way, and do not introduce Gradle/Maven
without maintainer approval.

Install pixi if missing:

- Linux/macOS: `curl -fsSL https://pixi.sh/install.sh | sh`
- Windows: `powershell -ExecutionPolicy Bypass -c "irm -useb https://pixi.sh/install.ps1 | iex"`

Rules:

- **`pixi.toml` and `pixi.lock` are git-tracked — always.** Commit both
  whenever dependencies or tasks change. Never add them to `.gitignore`.
- The `.pixi/` environment directory is ignored and must stay ignored.
- The lockfile is solved for `linux-64`, `win-64`, `osx-64`, `osx-arm64`;
  keep all four platforms when changing dependencies.
- The JDK comes from conda-forge (`openjdk >=25`, i.e. Java 25).

Tasks (run from the repo root):

- `pixi run build` — compiles `src/main/java` (entry `ttdrop.Main`) into
  `build/classes` and packages `dist/ttdrop.jar` with the embedded
  webroot from `src/main/resources`.
- `pixi run run` — builds then runs the jar.
- `pixi run clean` — removes `build/` and `dist/`.

The PWA has **no build step** (vanilla-only constraint): its assets live
in `src/main/resources/webroot/` and are packaged into the jar as-is.

There is no test suite yet; when one is added, wire it as a pixi task and
document it here. Until then, verify changes by building and running, and
state plainly in your summary what was and was not verified.

## Coding conventions

Not established yet. When the first real code lands, record here the Java
formatting/naming conventions and the PWA file layout so later agents stay
consistent. Until then, follow standard Java idioms and clean, dependency-
free browser JS/CSS.

## Testing

QR tests live in `tests/qr/` (`npm install` there once, then
`node qr.test.mjs`): encoder round-trips decoded with jsqr plus the
live `/qr.png` endpoint. Run them for any change touching
`ttdrop.util.QrCode` or `QrPngHandler`.

Browser tests live in `tests/browser/` (plain Node scripts, exit 0/1):
`upload.test.mjs`, `upload-resume.test.mjs`, `download-resume.test.mjs`,
shared setup in `lib.mjs`, orchestrated by `run.sh` (starts a headless
server on a temp dir, runs all three). They need Node.js, the
`playwright` package, and Chromium — deliberately not in the pixi env
to keep it lean. Run every one of them before finishing a batch that
touches transfer code, and add a test when adding a transfer behavior.

```sh
pixi run build
sh tests/browser/run.sh
```

## Cloud session notes (Claude Code remote containers)

Facts future agent sessions will otherwise rediscover the hard way:

- **pixi.sh is blocked** by the network policy (CONNECT 403). Install
  pixi by downloading the binary from GitHub releases instead:
  `https://github.com/prefix-dev/pixi/releases/latest/download/pixi-x86_64-unknown-linux-musl.tar.gz`
  → extract to `~/.pixi/bin/pixi` and add to PATH. conda-forge is
  reachable, so `pixi install` works normally after that.
- **Tag pushes are rejected** (403): the session git proxy only accepts
  pushes to the designated `claude/...` branch ref. Branch pushes work;
  `git push origin <tag>` cannot. The GitHub MCP toolset has no
  tag/release creation either. Hence the tags-on-maintainer-device rule.
- **Browser testing works in-container**: Node 22 at `/opt/node22`,
  global playwright module at
  `/opt/node22/lib/node_modules/playwright/index.mjs` (NODE_PATH does
  not apply to ESM imports — import by absolute path or set
  `PLAYWRIGHT_MODULE`), Chromium at
  `/opt/pw-browsers/chromium-1194/chrome-linux/chrome` (set
  `CHROMIUM_BIN`; the exact versioned directory may change — check
  `/opt/pw-browsers/`). `http://localhost` is a secure context, so
  OPFS and service workers are fully testable headlessly.
- **Do not `pkill -f` with a pattern that appears in your own command
  line** (e.g. `pkill -f ttdrop.jar`) — it kills your own shell. Kill
  Java test servers via `for pid in $(pgrep -x java); do kill $pid; done`.
- Java processes pick up proxy settings via `JAVA_TOOL_OPTIONS`
  automatically; harmless "Picked up JAVA_TOOL_OPTIONS" lines appear on
  stderr.

## Releases

`CHANGELOG` (no file extension, repo root) is the changelog and the
single source of truth for GitHub release notes. Section format:
`## vX.Y.Z — title (YYYY-MM-DD)` followed by bullet notes; batches
accumulate bullets under `## Unreleased`.

To cut a release: rename `## Unreleased` to the new version section
(start a fresh empty `## Unreleased` above it), bump `version` in
`pixi.toml`, merge to `main`, then trigger
`.github/workflows/release.yml` (workflow_dispatch). The workflow
parses CHANGELOG and, per version section, updates the existing GitHub
release's title/notes or creates a missing release tagged at the
triggering commit — so add one new version section per run and trigger
from the merge commit that should carry the tag. It also builds
`dist/ttdrop.jar` (Temurin 25) and attaches it to the newest release.
Editing past notes is fine: edit CHANGELOG, merge, re-trigger — the
sync is idempotent. This workflow exists because cloud sessions cannot
push tags; from a local device, plain `git tag` + `git push origin
<tag>` works too, but keep CHANGELOG authoritative for the notes.

## Directory map

```
ttDrop/
├── AGENT.md              # this guide
├── CHANGELOG             # changelog; source of GitHub release notes
├── LICENSE               # GPL-3.0
├── README.md             # user-facing description and quick start
├── .gitignore            # Java template + pixi env + build outputs
├── .gitattributes        # pixi.lock merge/linguist settings
├── pixi.toml             # pixi workspace: deps, platforms, tasks (tracked)
├── pixi.lock             # pixi lockfile, all 4 platforms (tracked)
├── tests/browser/        # Node+Playwright E2E tests (see Testing)
├── tests/qr/             # QR encoder + /qr.png decoder tests
└── src/main/
    ├── java/ttdrop/
    │   ├── Main.java             # entry point; GUI or --headless
    │   ├── Config.java           # ~/.config/ttdrop/config.properties
    │   ├── util/QrCode.java      # pure-JDK QR encoder (v1-5, ECC M)
    │   ├── gui/ServerWindow.java # Swing control window (IP picker, QR)
    │   └── server/
    │       ├── TtDropServer.java  # HttpServer wiring, LAN addresses
    │       ├── WebRootHandler.java# embedded PWA assets from the jar
    │       ├── FilesHandler.java  # /files/: listings, Range downloads
    │       ├── UploadHandler.java # /api/upload/: chunked resumable
    │       ├── FileOpsHandler.java# /api/files/: delete, rename
    │       ├── QrPngHandler.java  # /qr.png: QR of the site URL
    │       ├── CaCertHandler.java # /ca.crt: per-user CA download
    │       └── TlsSupport.java    # CA + server cert generation
    └── resources/webroot/
        ├── index.html            # app shell
        ├── style.css             # vanilla CSS, light/dark
        ├── app.js                # UI, browser, transfer orchestration
        ├── uploader.js           # upload worker (OPFS staging)
        ├── downloader.js         # download worker (OPFS staging)
        ├── sw.js                 # service worker (shell cache only)
        ├── manifest.webmanifest  # PWA manifest
        └── icon.svg              # app icon
```

Build outputs go to `build/` and `dist/` (both git-ignored).
Update this map when the source tree grows.

## Known gaps / good first tasks

- Downloading a whole folder (zip on the fly) from the browser.
- End-to-end integrity digests for uploads (e.g. client-computed
  SHA-256 verified at complete time, where crypto.subtle is available).
- The GUI is English-only; no localization scaffolding exists yet.
