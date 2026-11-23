# Shared helpers and stubs for the hermetic shell tests in tests/. Source it; it runs nothing on its own.
# Gives:
#   failures, fail <message>, check <what> <command…>  count and report failed checks
#   git_in <dir> <git args…>  git in a temp repo with a fixed identity, no signing and no hooks
#   install_stubs <dir>  writes the stubs apksigner, gradlew, docker and curl into <dir>
#   fake_key_command  a RELEASE_KEY_COMMAND that prints the base64 key that the apksigner stub accepts
# shellcheck shell=bash

failures=0
# shellcheck disable=SC2034 # used by the tests that source this file
fake_key_command="printf 'fake key\\n' | base64"
fail() { echo "FAIL: $*" >&2; failures=$((failures + 1)); }
check() { local what=$1; shift; "$@" || fail "$what"; }

git_in() {
  local dir=$1; shift
  git -C "$dir" -c user.name=stub-test -c user.email=stub-test@example.invalid -c commit.gpgsign=false \
    -c tag.gpgsign=false -c core.hooksPath=/dev/null -c init.defaultBranch=main "$@"
}

# Stubs. gradlew: runs only in $GRADLE_ROOT (the temp project root), fakes the
# unsigned APK (only with -PreleaseSigned=true, never with -Pmanifest); checkManifest asserts its arguments, writes "ok"
# or the reason to $STUB_LOG, and fails when STUB_CHECK=fail. Every call appends its arguments to $STUB_CALLS.
# With acceptBlocklist it also appends "local <-PlocalJson file as compact JSON>" and "candidate <-PblocklistCandidate
# file>", and fails when STUB_ACCEPT=fail. When STUB_SECRETS is set, every call appends "env <name>" for each of
# GITHUB_TOKEN and RELEASE_SIGNING_KEY in its environment and "login config <dir>" for each docker login config dir
# ("config" lines in $STUB_DOCKER) that still exists: the file stays empty when Gradle sees no secret.
# apksigner: "signs" by copying when the key arrives through --key; verify prints $STUB_DIGEST as the signer.
# docker: info fails when STUB_INFO=fail. build writes one line per option to $STUB_DOCKER ("tag <t>",
# "label <l>") and the resolved build context ("context <dir>"), and fails when STUB_BUILD=fail.
# login <registry> -u <user> --password-stdin: needs its own DOCKER_CONFIG dir and $STUB_TOKEN on stdin, writes
# "login <registry> <user>" and "config <dir>" to $STUB_DOCKER and a config.json into the dir, fails when
# STUB_LOGIN=fail. manifest inspect <ref>: needs that login, writes "manifest <ref>"; with STUB_MANIFEST=exists it
# prints a manifest, else it prints $STUB_MANIFEST (default: ghcr's "manifest unknown") on stderr and fails.
# curl: writes $STUB_BLOCKLIST to the file after -o and appends the URL to $STUB_URLS; fails when STUB_CURL=fail.
install_stubs() {
  local bin=$1
  mkdir -p "$bin" || return 1
  cat >"$bin/apksigner" <<'EOF'
#!/usr/bin/env bash
case "$1" in
  sign)
    shift
    while [[ $# -gt 1 ]]; do
      case "$1" in
        --key) [[ $(cat "$2") == "fake key" ]] || { echo "apksigner stub: wrong key" >&2; exit 1; }; shift 2 ;;
        --cert) shift 2 ;;
        --out) out=$2; shift 2 ;;
        *) echo "apksigner stub: unexpected $1" >&2; exit 1 ;;
      esac
    done
    cp "$1" "$out" ;;
  verify) echo "V3.0 Signer: certificate SHA-256 digest: $STUB_DIGEST" ;;
  *) echo "apksigner stub: unexpected $*" >&2; exit 1 ;;
esac
EOF
  cat >"$bin/gradlew" <<'EOF'
#!/usr/bin/env bash
echo "$*" >>"$STUB_CALLS"
if [[ -n ${STUB_SECRETS:-} ]]; then
  for name in GITHUB_TOKEN RELEASE_SIGNING_KEY; do [[ -z ${!name+set} ]] || echo "env $name"; done >>"$STUB_SECRETS"
  sed -n 's/^config //p' "$STUB_DOCKER" 2>/dev/null | while IFS= read -r dir; do
    [[ ! -e $dir ]] || echo "login config $dir"
  done >>"$STUB_SECRETS"
fi
[[ $(pwd -P) == $(cd "$GRADLE_ROOT" && pwd -P) ]] || { echo "gradlew stub: runs in $PWD, not in the project root" >&2; exit 1; }
if [[ " $* " == *" acceptBlocklist "* ]]; then
  for arg; do
    case $arg in
      -PlocalJson=*) echo "local $(jq -c . "${arg#-PlocalJson=}")" ;;
      -PblocklistCandidate=*) echo "candidate $(cat "${arg#-PblocklistCandidate=}")" ;;
    esac
  done >>"$STUB_CALLS"
  [[ ${STUB_ACCEPT:-} != fail ]] || { echo "gradlew stub: acceptBlocklist rejects the list" >&2; exit 1; }
fi
if [[ " $* " == *" assembleRelease "* ]]; then
  [[ " $* " == *" -PreleaseSigned=true "* ]] || { echo "gradlew stub: release without -PreleaseSigned=true" >&2; exit 1; }
  [[ " $* " != *" -Pmanifest="* ]] || { echo "gradlew stub: assembleRelease with -Pmanifest (the app embeds no catalog)" >&2; exit 1; }
  mkdir -p app/build/outputs/apk/release
  printf 'fake apk %s\n' "$*" >app/build/outputs/apk/release/app-release-unsigned.apk
  exit 0
fi
[[ ${1:-} == -q ]] && shift
if [[ $# -eq 4 && $1 == -p && $2 == build-logic && $3 == checkManifest && $4 == -Pmanifest=* ]]; then
  file=${4#-Pmanifest=}
  if [[ ! -f $file ]]; then echo "missing $file" >"$STUB_LOG"
  elif [[ $(cd "$(dirname "$file")" && pwd -P) != $(cd "$SERVER" && pwd -P) ]]; then echo "not in packages/server: $file" >"$STUB_LOG"
  elif [[ $(basename "$file") == manifest.json ]]; then echo "checked manifest.json itself" >"$STUB_LOG"
  else echo ok >"$STUB_LOG"; fi
  [[ ${STUB_CHECK:-} == fail ]] && { echo "stub: checkManifest fails" >&2; exit 1; }
  [[ $(cat "$STUB_LOG") == ok ]]
  exit
fi
echo "gradlew stub: unexpected $*" >&2
exit 1
EOF
  cat >"$bin/docker" <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
  info)
    echo info >>"$STUB_DOCKER"
    [[ ${STUB_INFO:-} != fail ]] || { echo "docker stub: daemon not running" >&2; exit 1; } ;;
  build)
    shift
    echo build >>"$STUB_DOCKER"
    while (($# > 1)); do
      case "$1" in
        -t) echo "tag $2" ;;
        --label) echo "label $2" ;;
        *) echo "docker stub: unexpected build option $1" >&2; exit 1 ;;
      esac >>"$STUB_DOCKER"
      shift 2
    done
    echo "context $(cd "$1" && pwd -P)" >>"$STUB_DOCKER"
    [[ ${STUB_BUILD:-} != fail ]] || { echo "docker stub: build fails" >&2; exit 1; } ;;
  login)
    [[ $# -eq 5 && $3 == -u && $5 == --password-stdin ]] || { echo "docker stub: unexpected login $*" >&2; exit 1; }
    [[ -n ${DOCKER_CONFIG:-} && -d $DOCKER_CONFIG ]] || { echo "docker stub: login without own DOCKER_CONFIG" >&2; exit 1; }
    [[ $(cat) == "${STUB_TOKEN:-}" ]] || { echo "docker stub: wrong password on stdin" >&2; exit 1; }
    printf 'login %s %s\nconfig %s\n' "$2" "$4" "$DOCKER_CONFIG" >>"$STUB_DOCKER"
    [[ ${STUB_LOGIN:-} != fail ]] || { echo "docker stub: login fails" >&2; exit 1; }
    printf '{"auths": {}}\n' >"$DOCKER_CONFIG/config.json"
    echo "Login Succeeded" ;;
  manifest)
    [[ $# -eq 3 && $2 == inspect ]] || { echo "docker stub: unexpected $*" >&2; exit 1; }
    echo "manifest $3" >>"$STUB_DOCKER"
    [[ -f ${DOCKER_CONFIG:-}/config.json ]] || { echo "docker stub: manifest inspect without login" >&2; exit 1; }
    [[ ${STUB_MANIFEST:-} != exists ]] || { echo '{"schemaVersion": 2}'; exit 0; }
    echo "${STUB_MANIFEST:-manifest unknown}" >&2
    exit 1 ;;
  *) echo "docker stub: unexpected $*" >&2; exit 1 ;;
esac
EOF
  cat >"$bin/curl" <<'EOF'
#!/usr/bin/env bash
out='' url=''
while (($#)); do
  case $1 in
    -o) out=$2; shift 2 ;;
    --max-time) shift 2 ;;
    -*) shift ;;
    *) url=$1; shift ;;
  esac
done
[[ -z ${STUB_URLS:-} ]] || echo "$url" >>"$STUB_URLS"
[[ ${STUB_CURL:-} != fail ]] || { echo "curl stub: (22) The requested URL returned error: 404" >&2; exit 22; }
printf '%s\n' "${STUB_BLOCKLIST:-}" >"$out"
EOF
  chmod +x "$bin/apksigner" "$bin/gradlew" "$bin/docker" "$bin/curl"
}
