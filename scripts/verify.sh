#!/usr/bin/env bash
#
# One run, every target, one report. Exit 0 only on PASS.
#
# A skip is always an explicit line in the report — never silence. The difference between "Android
# was not built" and "Android has no SDK here" is the whole value of this script, and a suite that
# quietly passes because it did nothing is worse than one that fails.
set -uo pipefail

cd "$(dirname "$0")/.."
out=build/verify
rm -rf "$out"
mkdir -p "$out"
report="$out/report.md"
failed=0

say()  { echo "$1" | tee -a "$report"; }
step() { say ""; say "## $1"; }
ok()   { say "- PASS: $1"; }
warn() { say "- WARN: $1"; }
skip() { say "- SKIPPED: $1"; }
bad()  { say "- FAIL: $1"; failed=1; }

say "# superizer — verify"
say ""
say "\`$(date -u '+%Y-%m-%d %H:%M:%SZ')\` · $(git rev-parse --short HEAD 2>/dev/null || echo 'no git')"

# ---------------------------------------------------------------- 1. static
step "1. Static"
if ./gradlew --quiet checkDependencyRules > "$out/static.log" 2>&1; then
  ok "dependency rules (02)"
else
  bad "dependency rules — see $out/static.log"
fi

# D14: a change to the public API that forgot to regenerate the dump fails here. The diff of
# `*/api/*.api` in a pull request is the review of the contract.
if ./gradlew --quiet checkLegacyAbi >> "$out/static.log" 2>&1; then
  ok "ABI dumps match the code"
else
  bad "ABI dumps are stale — run ./gradlew updateLegacyAbi and commit the diff"
fi

# D46: an ignored test is a test nobody will ever turn back on.
if grep -rn --include='*.kt' -E '@Ignore|\.skip\(' . --exclude-dir=build > "$out/ignored.txt" 2>/dev/null; then
  bad "disabled tests found — see $out/ignored.txt"
else
  ok "no @Ignore and no .skip("
fi

# ---------------------------------------------------------------- 2. tests
step "2. Unit and UI tests (desktop)"
if ./gradlew --quiet desktopTest --continue > "$out/test.log" 2>&1; then
  ok "desktop tests"
else
  bad "desktop tests — see $out/test.log"
fi
mkdir -p "$out/junit"
find . -path '*/build/test-results/*' -name '*.xml' -not -path "./$out/*" -exec cp {} "$out/junit/" \; 2>/dev/null
say "  ($(ls "$out/junit" 2>/dev/null | wc -l) result files)"

step "3. Unit tests (wasm)"
# Karma needs a browser. Playwright's Chromium is the one this workspace has; without it the wasm
# tests are skipped by name rather than by a green build that ran nothing.
chromium=$(ls -d "$HOME"/.cache/ms-playwright/chromium_headless_shell-*/chrome-linux/headless_shell 2>/dev/null | head -1)
if [ -n "$chromium" ]; then
  if CHROME_BIN="$chromium" ./gradlew --quiet wasmJsTest > "$out/wasm-test.log" 2>&1; then
    ok "wasm tests (catches java.* and ServiceLoader, which the JVM forgives)"
  else
    bad "wasm tests — see $out/wasm-test.log"
  fi
else
  skip "wasm tests: no Chromium in ~/.cache/ms-playwright"
fi

# ---------------------------------------------------------------- 4. web
step "4. Web (wasm bundle)"
if ./gradlew --quiet :fixture:wasmJsBrowserDistribution > "$out/web-build.log" 2>&1; then
  ok "fixture wasm bundle"
  dist=fixture/build/dist/wasmJs/productionExecutable
  if [ -n "$chromium" ] && [ -d "$dist" ]; then
    if node scripts/web-smoke.mjs "$dist" "$out/web" >> "$out/web-build.log" 2>&1; then
      ok "web smoke test — see $out/web/"
    else
      bad "web smoke test — see $out/web-build.log"
    fi
  else
    skip "web smoke test: no Chromium or no bundle"
  fi
  wasm=$(ls -S "$dist"/*.wasm 2>/dev/null | head -1)
  [ -n "$wasm" ] && say "  wasm bundle: $(( $(stat -c%s "$wasm") / 1024 )) KB"
else
  bad "fixture wasm bundle — see $out/web-build.log"
fi

# ---------------------------------------------------------------- 5. publish
step "5. Publish"
if ./gradlew --quiet publishToMavenLocal > "$out/publish.log" 2>&1; then
  ok "publishToMavenLocal"
  # The consumer build is the part that catches what a composite build forgives: a missing `api`
  # dependency, an absent sources jar, a klib with no metadata.
  if [ -d samples/consumer ]; then
    if ./gradlew --quiet -p samples/consumer build >> "$out/publish.log" 2>&1; then
      ok "samples/consumer builds from mavenLocal only"
    else
      bad "samples/consumer — see $out/publish.log"
    fi
  else
    skip "samples/consumer: not present"
  fi
else
  bad "publishToMavenLocal — see $out/publish.log"
fi

# ---------------------------------------------------------------- result
say ""
if [ "$failed" -eq 0 ]; then
  say "RESULT: PASS"
else
  say "RESULT: FAIL"
fi
exit "$failed"
