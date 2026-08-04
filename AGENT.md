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

The codebase is at the very beginning: as of now the repo contains only
`README.md` (title only), `LICENSE` (GPL-3.0), `.gitignore` (Java
template), and this guide. The server and PWA described above are the
intended design, not yet implemented.

Therefore: do not assume the existence of any module, build file, or
directory that is not present on disk — verify with the actual file tree
first. When the source tree, build system, or test setup is created,
update the sections below in the same commit.

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

## Build, test, and lint

Not configured yet. When introduced, document here:

- exact commands to build, test, and lint the Java server (and required
  JDK version);
- how the PWA assets are laid out and served (they should need no build
  step, per the vanilla-only constraint);
- how to run the server locally and reach the PWA from another device.

Until then, verify changes by whatever means the change allows, and state
plainly in your summary what was and was not verified.

## Coding conventions

Not established yet. When the first real code lands, record here the Java
formatting/naming conventions and the PWA file layout so later agents stay
consistent. Until then, follow standard Java idioms and clean, dependency-
free browser JS/CSS.

## Directory map

```
ttDrop/
├── AGENT.md      # this guide
├── LICENSE       # GPL-3.0
├── README.md     # project title (needs a real description)
└── .gitignore    # Java template
```

Update this map when the source tree grows.

## Known gaps / good first tasks

- README.md needs a real description (the "What ttDrop is" section above
  is a starting point), usage instructions, and a license notice.
- No build system or source layout exists yet — confirm the intended
  Java tooling (plain `javac`, Gradle, Maven, target JDK) with the
  maintainer before scaffolding.
