#!/bin/sh
# Runs all ttDrop browser tests against a freshly started server.
# Requires: dist/ttdrop.jar built (pixi run build), Node.js, playwright,
# and Chromium. See lib.mjs for the environment variables; in Claude
# cloud sessions:
#   PLAYWRIGHT_MODULE=/opt/node22/lib/node_modules/playwright/index.mjs
#   CHROMIUM_BIN=/opt/pw-browsers/chromium-1194/chrome-linux/chrome
set -eu

REPO_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PORT="${TTDROP_PORT:-4655}"
SERVE_DIR="${TTDROP_DIR:-$(mktemp -d)}"

# Prefer the project's pixi-managed JDK (matches the build's class version).
JAVA=java
[ -x "$REPO_ROOT/.pixi/envs/default/bin/java" ] && JAVA="$REPO_ROOT/.pixi/envs/default/bin/java"

cd "$SERVE_DIR"
"$JAVA" -jar "$REPO_ROOT/dist/ttdrop.jar" --headless --port "$PORT" &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT
sleep 3

cd "$REPO_ROOT/tests/browser"
FAIL=0
for test in upload.test.mjs upload-resume.test.mjs download-resume.test.mjs; do
    echo "=== $test ==="
    TTDROP_PORT="$PORT" TTDROP_DIR="$SERVE_DIR" node "$test" || FAIL=1
done
exit "$FAIL"
