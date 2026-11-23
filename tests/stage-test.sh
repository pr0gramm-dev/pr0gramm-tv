#!/usr/bin/env bash
# Hermetic test of mise-tasks/stage, the build step of release.yml: admission (version check), blocklist, signing,
# the app version from the tag (AC10), the self-update repo from GITHUB_REPOSITORY, no manifest, no apks/ and no registry (AC13), no secret for Gradle.
# Uses the stubs from tests/stubs.sh and a temp git root. Needs only bash, git, jq, openssl and shasum.
# Run: bash tests/stage-test.sh
set -uo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/stage-test.XXXXXX") || exit 1
trap 'rm -rf "$work"' EXIT
# shellcheck source=tests/stubs.sh
source "$repo/tests/stubs.sh"
install_stubs "$work/bin" || { echo "stage-test: install_stubs failed" >&2; exit 1; }
# docker on PATH only records that stage called it: stage must not.
mkdir -p "$work/path"
printf '#!/usr/bin/env bash\necho "docker $*" >>"$STUB_DOCKER"\n' >"$work/path/docker"
chmod +x "$work/path/docker"

# A temp root with the app's build.gradle.kts, an old blocklist copy, a throwaway release-cert.pem, one commit on main,
# origin/main and the tag v0.2.0 on that commit, and the older tag v0.1.0.
stage_root=$work/stage
apk_out=app/build/outputs/apk/release/pr0gramm-tv.apk
openssl req -new -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -keyout "$work/key.pem" -days 1 \
  -subj "/CN=stage-test" -out "$work/cert.pem" 2>/dev/null || { echo "stage-test: openssl failed" >&2; exit 1; }
digest=$(openssl x509 -in "$work/cert.pem" -outform DER | shasum -a 256 | cut -d' ' -f1)
# The test sets the CI variables itself; values from the calling shell must not leak in.
unset GITHUB_TOKEN GITHUB_ACTOR GITHUB_REPOSITORY RELEASE_SIGNING_KEY BLOCKLIST_URL
key=$(printf 'fake key\n' | base64)
url=https://lists.test/domains.txt
repo_name=fork-owner/fork
setup_stage() {
  rm -rf "$stage_root" "$work"/stage-*.log
  mkdir -p "$stage_root/config" "$stage_root/app"
  cp "$repo/app/build.gradle.kts" "$stage_root/app/build.gradle.kts"
  printf 'old list\n' >"$stage_root/blocklist.txt"
  cp "$work/cert.pem" "$stage_root/config/release-cert.pem"
  git_in "$stage_root" init -q && git_in "$stage_root" add -A && git_in "$stage_root" commit -qm fixture &&
    git_in "$stage_root" update-ref refs/remotes/origin/main HEAD && git_in "$stage_root" tag v0.1.0 &&
    git_in "$stage_root" tag v0.2.0
}

# Runs stage with the CI environment. The caller can override: RELEASE_SIGNING_KEY, BLOCKLIST_URL, GITHUB_REPOSITORY
# and the stub
# switches STUB_CURL, STUB_ACCEPT, STUB_DIGEST. RELEASE_KEY_COMMAND fails: stage must give the key to sign through
# RELEASE_SIGNING_KEY.
run_stage() {
  PATH="$work/path:$PATH" MISE_PROJECT_ROOT=$stage_root GRADLEW=$work/bin/gradlew APKSIGNER=$work/bin/apksigner \
    CURL=$work/bin/curl RELEASE_KEY_COMMAND='echo "RELEASE_KEY_COMMAND ran" >&2; exit 1' \
    RELEASE_SIGNING_KEY=${RELEASE_SIGNING_KEY-$key} BLOCKLIST_URL=${BLOCKLIST_URL-$url} \
    GITHUB_REPOSITORY=${GITHUB_REPOSITORY-$repo_name} \
    STUB_CURL=${STUB_CURL:-} STUB_ACCEPT=${STUB_ACCEPT:-} STUB_DIGEST=${STUB_DIGEST:-$digest} \
    STUB_BLOCKLIST='ads.test' STUB_URLS=$work/stage-urls.log STUB_DOCKER=$work/stage-docker.log \
    STUB_CALLS=$work/stage-calls.log STUB_SECRETS=$work/stage-secrets.log \
    GRADLE_ROOT=$stage_root bash "$repo/mise-tasks/stage" "$@" >"$work/stage-out.log" 2>"$work/stage-err.log"
}
err_has() { grep -qF -- "$1" "$work/stage-err.log"; }
no_gradle() { [ ! -e "$work/stage-calls.log" ]; }
gradle_arg() { grep assembleRelease "$work/stage-calls.log" | grep -qE -- "(^| )$1( |\$)"; }
no_secret_in_logs() { ! grep -qF -e "$key" -e "fake key" -e stub-token-4711 "$work/stage-out.log" "$work/stage-err.log"; }
# Expects stage to stop with a message that contains $2 and to leave no signed APK. Arguments: label, message, stage
# arguments.
stage_rejects() {
  local what=$1 message=$2; shift 2
  if run_stage "$@"; then
    fail "$what: stage exited 0"
  else
    check "$what: message \"$message\" in: $(cat "$work/stage-err.log")" err_has "$message"
    check "$what: nothing on stdout" [ ! -s "$work/stage-out.log" ]
    check "$what: no signed APK" [ ! -e "$stage_root/$apk_out" ]
  fi
}

# (stage a) success: stdout carries only the step outputs, the signed APK is pr0gramm-tv.apk, Gradle gets the version
# from the tag, the blocklist from BLOCKLIST_URL and no secret. stage needs no registry login and writes no catalog:
# a GITHUB_TOKEN in the environment does not reach Gradle either.
setup_stage
if GITHUB_TOKEN=stub-token-4711 run_stage v0.2.0; then
  check "(stage a) stdout" [ "$(cat "$work/stage-out.log")" = "$(printf 'version=0.2.0\napk=%s' "$apk_out")" ]
  check "(stage a) signed APK" grep -q '^fake apk .*assembleRelease' "$stage_root/$apk_out"
  check "(stage a) acceptBlocklist" gradle_arg acceptBlocklist
  check "(stage a) -PappVersion" gradle_arg -PappVersion=0.2.0
  check "(stage a) -PupdateRepo from GITHUB_REPOSITORY" gradle_arg -PupdateRepo=$repo_name
  check "(stage a) no -PversionCode, no -PgitHash" [ "$(grep -cE -- '-P(versionCode|gitHash)=' "$work/stage-calls.log")" = 0 ]
  check "(stage a) one Gradle call" [ "$(grep -c assembleRelease "$work/stage-calls.log")" = 1 ]
  check "(stage a) no checkManifest" [ "$(grep -c checkManifest "$work/stage-calls.log")" = 0 ]
  check "(stage a) blocklist URL" [ "$(cat "$work/stage-urls.log")" = "$url" ]
  check "(stage a) Gradle local config" grep -qxF "local {\"blocklist\":\"$url\"}" "$work/stage-calls.log"
  check "(stage a) Gradle candidate" grep -qx 'candidate ads.test' "$work/stage-calls.log"
  check "(stage a) old blocklist copy removed" [ ! -e "$stage_root/blocklist.txt" ]
  check "(stage a) no docker call: $(cat "$work/stage-docker.log" 2>/dev/null)" [ ! -e "$work/stage-docker.log" ]
  check "(stage a) no packages/ written" [ ! -e "$stage_root/packages" ]
  check "(stage a) no manifest.json written" [ -z "$(cd "$stage_root" && find . -name manifest.json -not -path './.git/*')" ]
  check "(stage a) Gradle sees no secret: $(cat "$work/stage-secrets.log")" [ ! -s "$work/stage-secrets.log" ]
  check "(stage a) no secret in the logs" no_secret_in_logs
else
  fail "(stage a) stage exited non-zero: $(cat "$work/stage-err.log")"
fi

# (stage b) every admission stop comes before Gradle.
setup_stage
stage_rejects "(stage b) usage" "usage: stage <tag>"
check "(stage b) usage: no Gradle" no_gradle
stage_rejects "(stage b) format" 'vMAJOR.MINOR.PATCH' v2
check "(stage b) format: no Gradle" no_gradle
stage_rejects "(stage b) not above v0.2.0" v0.2.0 v0.1.0
check "(stage b) not above v0.2.0: no Gradle" no_gradle
setup_stage
git_in "$stage_root" commit -q --allow-empty -m later && git_in "$stage_root" update-ref refs/remotes/origin/main HEAD
stage_rejects "(stage b) HEAD is not the tag" "HEAD" v0.2.0
check "(stage b) HEAD is not the tag: no Gradle" no_gradle

# (stage c) no key, no blocklist URL, a failed download or a rejected list stop the run.
setup_stage
RELEASE_SIGNING_KEY='' stage_rejects "(stage c) empty key" RELEASE_SIGNING_KEY v0.2.0
check "(stage c) empty key: no Gradle" no_gradle
setup_stage
BLOCKLIST_URL='' stage_rejects "(stage c) no BLOCKLIST_URL" BLOCKLIST_URL v0.2.0
check "(stage c) no BLOCKLIST_URL: no Gradle" no_gradle
setup_stage
GITHUB_REPOSITORY='' stage_rejects "(stage c) no GITHUB_REPOSITORY" GITHUB_REPOSITORY v0.2.0
check "(stage c) no GITHUB_REPOSITORY: no Gradle" no_gradle
setup_stage
STUB_CURL=fail stage_rejects "(stage c) download fails" "$url" v0.2.0
check "(stage c) download fails: no Gradle" no_gradle
setup_stage
STUB_ACCEPT=fail stage_rejects "(stage c) list rejected" "Build failed" v0.2.0
check "(stage c) list rejected: old copy is no fallback" [ ! -e "$stage_root/blocklist.txt" ]

# (stage d) the signer is not release-cert.pem: no APK, the key is not in the logs.
setup_stage
STUB_DIGEST=0000 stage_rejects "(stage d) wrong signer" "does not match config/release-cert.pem" v0.2.0
check "(stage d) no secret in the logs" no_secret_in_logs

if ((failures)); then echo "stage-test: $failures failure(s)" >&2; exit 1; fi
echo "stage-test: ok"
