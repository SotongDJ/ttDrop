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

1. Install [pixi](https://pixi.sh) and run `pixi run build` in this repo
   to produce `dist/ttdrop.jar` (needs no other tooling — pixi provides
   the JDK).
2. Copy `ttdrop.jar` into the folder you want to share, e.g.
   `N:\the\target\path` or `/the/target/path`. That folder becomes the
   file area: received files land there, and files placed there are
   downloadable.
3. Run the jar (double-click, or `java -jar ttdrop.jar`). Press
   **Start** in the window (or use `--headless` on servers).
4. On another device, open the URL the window shows
   (`http://<your-ip>:4646/`). Drop files to send; tap listed files to
   download.

## How transfers work

- Files are split into chunks and transferred over **parallel
  connections**, in both directions.
- Transfers are **resumable**: interrupted uploads and downloads
  continue from the chunks already done — across page reloads (staged
  in the browser's private storage, OPFS) and across server restarts
  (staged in a hidden `.ttdrop-part/` folder).
- The web app is **vanilla JS/CSS with browser-native APIs only** — no
  external libraries, no CDN, fully served by the jar itself.

## Notes

- Configuration is stored in `~/.config/ttdrop/`, never in the shared
  folder.
- On plain HTTP, browsers only grant OPFS/service-worker features to
  `localhost`; other devices still transfer files, but without
  reload-resume. See `AGENT.md` for the roadmap on this.
- Developers and LLM agents: read [`AGENT.md`](AGENT.md) before making
  changes.

## License

ttDrop is free software, released under the
[GNU General Public License v3.0](LICENSE).
