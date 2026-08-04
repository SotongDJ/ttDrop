# ttDrop

Local file transfer between your devices — no cloud, no accounts, no
internet required.

ttDrop is a small **Java server app** (Windows/macOS/Linux) plus a
**browser PWA** (PC/Mac/iPhone/iPad/Android). Start the server on one
computer; every other device on your network opens a web page that can
**send files to** and **download files from** that computer — with
chunked, parallel, resumable transfers that survive dropped connections
and page reloads.

## Quick start

1. Get `ttdrop.jar` from the
   [latest release](https://github.com/SotongDJ/ttDrop/releases/latest)
   (needs Java 25 or newer), or build it yourself — see
   [Development](#development).
2. Copy `ttdrop.jar` into the folder you want to share, e.g.
   `N:\the\target\path` or `/the/target/path`. That folder becomes the
   file area: received files land there, and files placed there are
   downloadable.
3. Run the jar (double-click, or `java -jar ttdrop.jar`). Press
   **Start** in the window — or run `java -jar ttdrop.jar --headless`
   on machines without a display (`--port <n>` to override the port,
   default 4646; `--http` to disable TLS).
4. On another device, open the URL the window shows — or scan the QR
   code it displays (`https://<your-ip>:4646/`). Accept the one-time
   certificate warning, or better: tap **Install the ttDrop
   certificate** in the page footer (also at `/ca.crt`) and trust it —
   ttDrop generates its own per-user certificate authority, so
   installing that one certificate removes the warnings on every
   future session and lets the page install as an app. Nothing leaves
   your network. Drop files onto the page to send; tap listed files to
   download.

## Features

- **Chunked, parallel, resumable transfers** in both directions:
  interrupted uploads and downloads continue from the chunks already
  done — across page reloads (staged in the browser's private storage,
  OPFS) and across server restarts (staged in a hidden `.ttdrop-part/`
  folder). Any transfer can be cancelled with full cleanup.
- **Folder uploads**: pick a folder or drag one onto the page; the
  directory structure is recreated on the server.
- **Manage server files from the browser**: rename and delete
  (recursively for folders) right from the file list.
- **Share by QR**: the server window shows a QR code of its URL, and
  the page itself can display one (`/qr.png`) to pass to other devices.
- **Choose what you share**: change the shared folder from the window,
  set it with `--root <dir>`, or just drop the jar into the folder;
  optional start-on-launch.
- The web app is **vanilla JS/CSS with browser-native APIs only** — no
  external libraries, no CDN, fully served by the jar itself.

## Notes

- Configuration is stored in `~/.config/ttdrop/` (the last used port),
  never in the shared folder.
- On plain HTTP, browsers only grant OPFS/service-worker features to
  `localhost`; other devices still transfer files, but without
  reload-resume or app install. See `AGENT.md` for the roadmap on this.

## Development

The toolchain is managed by [pixi](https://pixi.sh) — it provides the
JDK, nothing else needs installing:

```sh
pixi run build   # produces dist/ttdrop.jar
pixi run run     # build + run
sh tests/browser/run.sh   # end-to-end browser tests (needs Node + Playwright)
```

- [`AGENT.md`](AGENT.md) — the maintainer guide: architecture, hard
  constraints, transfer protocols, workflow rules. Read it first,
  human or LLM.
- [`CHANGELOG`](CHANGELOG) — the changelog, and the source of truth
  for the notes on the
  [releases page](https://github.com/SotongDJ/ttDrop/releases);
  releases are cut by the manually triggered `release` GitHub Actions
  workflow.

## License

ttDrop is free software, released under the
[GNU General Public License v3.0](LICENSE).
