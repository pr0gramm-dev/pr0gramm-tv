#!/usr/bin/env bash
# Hermetic test of the workflows. release.yml (AC11): workflow text and the release step with a gh stub.
# release-please.yml: workflow text, the dispatch step with a gh stub, and the release-please config.
# ci.yml (AC12): workflow text. Uses the helpers from tests/stubs.sh. Needs only bash, awk and jq.
# Run: bash tests/ci-test.sh
set -uo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/ci-test.XXXXXX") || exit 1
trap 'rm -rf "$work"' EXIT
# shellcheck source=tests/stubs.sh
source "$repo/tests/stubs.sh"

# Token of the stubbed gh in the step script below.
token=stub-token-4711

# Workflows. block <key>: prints the line "<key>:…" of stdin and every following line that is indented deeper. <key>
# carries its indentation, e.g. "  build" for a job. job <name> [file]: the block of a job in the file (default
# release.yml). perms <job> [file]: the permissions of a job on one line, e.g. "contents: read".
wf=$repo/.github/workflows/release.yml
ci=$repo/.github/workflows/ci.yml
rp=$repo/.github/workflows/release-please.yml
block() {
  awk -v key="$1:" '!on && index($0, key) == 1 { match($0, /^ */); ind = RLENGTH; on = 1; print; next }
    on { if ($0 ~ /[^ ]/ && match($0, /^ */) && RLENGTH <= ind) exit; print }'
}
job() { block jobs <"${2:-$wf}" | block "  $1"; }
perms() { job "$1" "${2:-$wf}" | block "    permissions" | tail -n +2 | sed 's/^ *//' | paste -sd, -; }
# none <regex> <file…>: no line matches (case-insensitive); a missing file fails.
has() { grep -qxF -- "$2" <<<"$1"; }; lacks() { ! grep -qF -- "$2" <<<"$1"; }
none() { grep -qiE -- "$@"; [[ $? == 1 ]]; }
# in_order <text> <part…>: every part is in <text>, each on a later line than the part before it.
in_order() {
  local text=$1 prev=0 n; shift
  for part; do n=$(grep -nF -- "$part" <<<"$text" | head -1 | cut -d: -f1); [[ -n $n && $n -gt $prev ]] || return 1; prev=$n; done
}
# uses <file>: the actions that the file uses, one per line.
uses() { sed -n 's/^ *\(- \)\{0,1\}uses: //p' "$1"; }

# release.yml (AC11).
check "(release.yml) a tag v* or a dispatch starts it" [ "$(block on <"$wf")" = \
  "$(printf 'on:\n  push:\n    tags:\n      - "v*"\n  workflow_dispatch:')" ]
check "(release.yml) build runs only for a tag" has "$(job build)" "    if: github.ref_type == 'tag'"
check "(release.yml) one run at a time" [ "$(block concurrency <"$wf")" = \
  "$(printf 'concurrency:\n  group: release\n  cancel-in-progress: false')" ]
check "(release.yml) shell bash -eo pipefail" [ "$(block defaults <"$wf")" = "$(printf 'defaults:\n  run:\n    shell: bash')" ]
check "(release.yml) no permissions by default" grep -qx 'permissions: {}' "$wf"
check "(release.yml) jobs" [ "$(block jobs <"$wf" | grep -E '^  [a-z]')" = "$(printf '  build:\n  release:')" ]
check "(release.yml) build: only contents: read" [ "$(perms build)" = "contents: read" ]
check "(release.yml) release: only contents: write" [ "$(perms release)" = "contents: write" ]
check "(release.yml) release after build" has "$(job release)" '    needs: build'
check "(release.yml) checkout with all tags" has "$(job build)" '          fetch-depth: 0'
check "(release.yml) checkout keeps no credentials" has "$(job build)" '          persist-credentials: false'
check "(release.yml) no checkout in release" lacks "$(job release)" actions/checkout
check "(release.yml) key secret" has "$(job build)" '          RELEASE_SIGNING_KEY: ${{ secrets.RELEASE_SIGNING_KEY }}'
check "(release.yml) build runs in the environment release" has "$(job build)" '    environment: release'
check "(release.yml) only build has an environment" lacks "$(job release)" environment:
check "(release.yml) key secret only in build" lacks "$(job release)" RELEASE_SIGNING_KEY
check "(release.yml) blocklist variable" has "$(job build)" '          BLOCKLIST_URL: ${{ vars.BLOCKLIST_URL }}'
check "(release.yml) build: stage, then upload of the signed APK" in_order "$(job build)" \
  '        run: bash mise-tasks/stage "$GITHUB_REF_NAME" >>"$GITHUB_OUTPUT"' 'actions/upload-artifact@v4' \
  '          path: ${{ steps.stage.outputs.apk }}'
check "(release.yml) one artifact, the APK" [ "$(grep -cF 'actions/upload-artifact@v4' "$wf")" = 1 ]
check "(release.yml) no image, no registry, no catalog" none 'docker|ghcr|image|packages:|manifest|smoke|apks/' "$wf"
check "(release.yml) only the allowed actions" [ "$(uses "$wf" |
  grep -vxE 'actions/(checkout|setup-java|upload-artifact|download-artifact)@v4')" = "" ]
check "(release.yml) no commit, no push to a branch" none 'git (commit|push|tag)|persist-credentials: true' "$wf"

# The release step with a gh stub that appends its arguments to $STUB_WF.
mkdir -p "$work/wf-bin" "$work/wf"
# "gh release view" succeeds only when STUB_VIEW=exists (a draft release of release-please exists).
cat >"$work/wf-bin/gh" <<'EOF'
#!/usr/bin/env bash
echo "gh $*" >>"$STUB_WF"
[[ "${1:-} ${2:-}" != "release view" || ${STUB_VIEW:-missing} == exists ]]
EOF
chmod +x "$work/wf-bin/gh"
# step_run <name> [file]: the "run: |" script of the step "- name: <name>" in the file (default release.yml), without
# its indentation.
step_run() {
  awk -v name="      - name: $1" '$0 == name { found = 1; next }
    found && !ind && /^ *run: \|$/ { match($0, /^ */); ind = RLENGTH + 2; next }
    ind { if ($0 ~ /[^ ]/ && match($0, /^ */) && RLENGTH < ind) exit; print substr($0, ind + 1) }' "${2:-$wf}"
}
# run_step <name> <file> [VAR=value…]: runs the step script like the workflow (bash -eo pipefail) in $work/wf with
# the stub.
run_step() {
  local script; script=$(step_run "$1" "$2"); shift 2
  rm -f "$work/wf/calls.log"
  [[ -n $script ]] || { echo "no run script" >"$work/wf/out.log"; return 1; }
  (cd "$work/wf" && env PATH="$work/wf-bin:$PATH" STUB_WF="$work/wf/calls.log" "$@" bash -eo pipefail -c "$script") \
    >"$work/wf/out.log" 2>&1
}
calls() { cat "$work/wf/calls.log" 2>/dev/null; }
release_env=(VERSION=0.2.0 GITHUB_REF_NAME=v0.2.0 GITHUB_REPOSITORY=fork-owner/fork GH_TOKEN=$token)
# (a) no release of the tag yet (a tag pushed by hand): create it with exactly the asset pr0gramm-tv.apk.
if run_step "GitHub Release" "$wf" "${release_env[@]}"; then
  check "(release.yml) new release v0.2.0 in \$GITHUB_REPOSITORY with exactly the asset pr0gramm-tv.apk: $(calls)" \
    grep -qE '^gh release create v0\.2\.0 apk/pr0gramm-tv\.apk --repo fork-owner/fork --verify-tag( |$)' \
    "$work/wf/calls.log"
  check "(release.yml) new release: two gh calls, view and create" [ "$(calls | wc -l | tr -d ' ')" = 2 ]
  check "(release.yml) no token in the log" lacks "$(cat "$work/wf/out.log")" "$token"
else fail "(release.yml) new release exited non-zero: $(cat "$work/wf/out.log")"; fi
# (b) the draft release of release-please exists: upload the APK, then publish the draft as the latest release.
if run_step "GitHub Release" "$wf" "${release_env[@]}" STUB_VIEW=exists; then
  check "(release.yml) draft: view, upload, publish, in this order: $(calls)" in_order "$(calls)" \
    'gh release view v0.2.0 --repo fork-owner/fork' \
    'gh release upload v0.2.0 apk/pr0gramm-tv.apk --repo fork-owner/fork --clobber' \
    'gh release edit v0.2.0 --repo fork-owner/fork --draft=false --latest'
  check "(release.yml) draft: no new release" lacks "$(calls)" 'gh release create'
else fail "(release.yml) draft release exited non-zero: $(cat "$work/wf/out.log")"; fi

# release-please.yml: a push to main updates the release PR; the merge of the release PR creates the tag and a draft
# release. The dispatch then starts release.yml on the tag, because a tag of GITHUB_TOKEN starts no workflow.
check "(release-please.yml) a push to main starts it" [ "$(block on <"$rp")" = \
  "$(printf 'on:\n  push:\n    branches:\n      - main')" ]
check "(release-please.yml) one run at a time" [ "$(block concurrency <"$rp")" = \
  "$(printf 'concurrency:\n  group: release-please\n  cancel-in-progress: false')" ]
check "(release-please.yml) no permissions by default" grep -qx 'permissions: {}' "$rp"
check "(release-please.yml) jobs" [ "$(block jobs <"$rp" | grep -E '^  [a-z]')" = "$(printf '  release-please:\n  dispatch:')" ]
check "(release-please.yml) release-please: contents, issues and pull requests" \
  [ "$(perms release-please "$rp")" = "contents: write,issues: write,pull-requests: write" ]
check "(release-please.yml) dispatch: only actions: write" [ "$(perms dispatch "$rp")" = "actions: write" ]
check "(release-please.yml) dispatch after release-please, only for a new release" in_order "$(job dispatch "$rp")" \
  '    needs: release-please' "    if: needs.release-please.outputs.release_created == 'true'"
check "(release-please.yml) only the allowed actions" [ "$(uses "$rp" | grep -vxF 'googleapis/release-please-action@v4')" = "" ]
check "(release-please.yml) config files" in_order "$(job release-please "$rp")" \
  '          config-file: release-please-config.json' '          manifest-file: .release-please-manifest.json'
check "(release-please.yml) no checkout, no secret but GITHUB_TOKEN, no environment" none \
  'actions/checkout|secrets\.[A-FH-Z]|environment:' "$rp"
if run_step "Start the release build" "$rp" TAG=v0.2.0 GITHUB_REPOSITORY=fork-owner/fork GH_TOKEN=$token; then
  check "(release-please.yml) dispatch release.yml on the tag: $(calls)" [ "$(calls)" = \
    'gh workflow run release.yml --repo fork-owner/fork --ref v0.2.0' ]
else fail "(release-please.yml) dispatch exited non-zero: $(cat "$work/wf/out.log")"; fi

# The release-please config: tags vX.Y.Z, draft releases with their tag at once, breaking changes before 1.0 are minor.
cfg=$repo/release-please-config.json
check "(release-please config) the first release is 0.1.0, not the default 1.0.0" \
  jq -e '."initial-version" == "0.1.0"' "$cfg"
check "(release-please config) simple, tag vX.Y.Z, draft with tag, minor bump before 1.0" jq -e '
  ."release-type" == "simple" and ."include-component-in-tag" == false and .draft == true and
  ."force-tag-creation" == true and ."bump-minor-pre-major" == true and (.packages | keys) == ["."]' "$cfg"
check "(release-please config) manifest has the root package" jq -e '.["."] | type == "string"' \
  "$repo/.release-please-manifest.json"

# ci.yml (AC12): mise run test on every push and every pull request, read only, no secret.
check "(ci.yml) exists" [ -f "$ci" ]
check "(ci.yml) every push and every pull request start it" grep -qx 'on: \[push, pull_request\]' "$ci"
check "(ci.yml) no permissions by default" grep -qx 'permissions: {}' "$ci"
check "(ci.yml) shell bash -eo pipefail" [ "$(block defaults <"$ci")" = "$(printf 'defaults:\n  run:\n    shell: bash')" ]
check "(ci.yml) one job test" [ "$(block jobs <"$ci" | grep -E '^  [a-z]')" = '  test:' ]
check "(ci.yml) test: only contents: read" [ "$(perms test "$ci")" = "contents: read" ]
check "(ci.yml) checkout keeps no credentials" has "$(job test "$ci")" '          persist-credentials: false'
check "(ci.yml) mise installs the tools, then SDK packages, then mise run test" in_order "$(job test "$ci")" \
  'actions/checkout@v4' 'jdx/mise-action@v2' '"platforms;android-37.0" "build-tools;37.0.0"' \
  '        run: mise run test'
check "(ci.yml) only the allowed actions" [ "$(uses "$ci" | grep -vxE 'actions/checkout@v4|jdx/mise-action@v2')" = "" ]
check "(ci.yml) no secret, no environment, no write" none 'secrets\.|environment:|: write|persist-credentials: true' "$ci"
check "(ci.yml) no commit, no push to a branch" none 'git (commit|push|tag)' "$ci"

left=$(git -C "$repo" grep -nP 'mise run (release|publish)(?![\w-])|release[-]test|publish[-]test|mise-tasks/(release|publish)(?![\w-])' -- ':!.specs')
check "(AC11) no mention of the removed release and publish: $left" [ -z "$left" ]

if ((failures)); then echo "ci-test: $failures failure(s)" >&2; exit 1; fi
echo "ci-test: ok"
