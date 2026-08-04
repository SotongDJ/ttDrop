# AGENT.md — Guide for LLM Agents

This file is the entry point for any LLM agent (Claude, Copilot, Cursor, etc.)
working on this repository. Read it fully before making changes, and keep it
up to date as the project evolves.

## Project overview

**ttDrop** is an early-stage project owned by [SotongDJ](https://github.com/SotongDJ).

Current state of the repository:

- `README.md` — project title only; no description yet.
- `LICENSE` — GNU General Public License v3.0 (GPL-3.0).
- `.gitignore` — Java template (ignores `*.class`, `*.jar`, build archives,
  JVM crash logs), which suggests the project is expected to be Java-based.
- No source code, build system, tests, or CI have been added yet.

Because the codebase is still empty, do not assume the existence of any
module, framework, or tooling that is not present on disk. Verify with the
actual file tree before referencing anything.

## Ground rules

1. **Never invent project facts.** If the README or code does not answer a
   question (e.g. what ttDrop does, which framework it uses), check the file
   tree and git history first; if still unclear, ask the maintainer rather
   than guessing.
2. **Respect the license.** All contributions are under GPL-3.0. Do not add
   code copied from incompatibly-licensed sources.
3. **Keep this file current.** Whenever you add or change something
   structural — a build system, a source layout, a test framework, CI, a
   release process — update the relevant section of this AGENT.md in the
   same commit. An outdated agent guide is worse than none.
4. **Small, reviewable changes.** Prefer focused commits with clear messages
   over large mixed changes.
5. **Do not commit secrets or generated artifacts.** Build outputs matching
   `.gitignore` patterns must never be force-added.

## Git workflow

- The default branch is `main`. Never commit directly to `main` unless the
  maintainer explicitly says so.
- Develop on a feature branch (agents in managed environments are usually
  assigned a `claude/...` branch — use the one you were given).
- Push with `git push -u origin <branch-name>` and open a pull request only
  when asked to.
- Write commit messages in the imperative mood ("Add drop handler", not
  "Added drop handler"), with a short subject line and an optional body
  explaining *why*.

## Build, test, and lint

There is no build system, test suite, or linter configured yet.

When one is introduced, document here:

- the exact commands to build, test, and lint (e.g. `./gradlew build`,
  `mvn test`);
- required toolchain versions (JDK version, etc.);
- how to run the application locally.

Until then, verify changes by whatever means the change itself allows
(compiling standalone files, reading carefully), and say plainly in your
summary what was and was not verified.

## Coding conventions

None are established yet. When the first real code lands:

- follow the idioms of the language and framework chosen;
- record any project-specific conventions (formatting tool, naming rules,
  package structure) in this section so later agents stay consistent.

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

- README.md needs a real project description, usage instructions, and a
  license notice.
- No build system or source layout exists yet — confirm the intended
  language and tooling with the maintainer before scaffolding one.
