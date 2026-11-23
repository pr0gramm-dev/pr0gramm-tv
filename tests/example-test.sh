#!/usr/bin/env bash
# Test of the example catalog (AC14): mise-tasks/check-manifest runs the real checkManifest of build-logic on
# example/manifest.json. Without --apks the check is green; with --apks it must miss apks/kodi.apk, which is not in git.
# Needs Java for Gradle (mise). Run: bash tests/example-test.sh
set -uo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
task=$repo/mise-tasks/check-manifest
work=$(mktemp -d "${TMPDIR:-/tmp}/example-test.XXXXXX") || exit 1
trap 'rm -rf "$work"' EXIT
# shellcheck source=tests/stubs.sh
source "$repo/tests/stubs.sh"

# Runs check-manifest from directory $1 with the remaining arguments; output in $work/out.log.
run_check() {
  local dir=$1; shift
  (cd "$dir" && MISE_PROJECT_ROOT=$repo MISE_ORIGINAL_CWD=$dir bash "$task" "$@") >"$work/out.log" 2>&1
}
log_has() { grep -qF -- "$1" "$work/out.log"; }

# (a) The example is green without the APK check, also with a path relative to a subdirectory.
run_check "$repo" example/manifest.json || fail "(a) check-manifest example/manifest.json failed: $(cat "$work/out.log")"
check "(a) reports in Ordnung: $(cat "$work/out.log")" log_has "manifest.json: in Ordnung"
run_check "$repo/example" manifest.json || fail "(a) check-manifest manifest.json in example/ failed: $(cat "$work/out.log")"

# (b) With --apks the check reads the APKs: apks/kodi.apk is missing, so it fails and names the file.
if run_check "$repo" example/manifest.json --apks; then fail "(b) check-manifest --apks exited 0"; fi
check "(b) names apks/kodi.apk: $(cat "$work/out.log")" log_has "apks/kodi.apk: file missing"

# (c) wrong usage and a missing file stop before Gradle.
for args in "" "example/manifest.json --foo" "a.json b.json" "--apks"; do
  # shellcheck disable=SC2086
  if run_check "$repo" $args; then fail "(c) check-manifest $args exited 0"; fi
done
if run_check "$repo" example/missing.json; then fail "(c) check-manifest on a missing file exited 0"; fi
check "(c) names the missing file: $(cat "$work/out.log")" log_has "example/missing.json"

if ((failures)); then
  echo "example-test: $failures failure(s)." >&2
  exit 1
fi
echo "example-test: ok"
