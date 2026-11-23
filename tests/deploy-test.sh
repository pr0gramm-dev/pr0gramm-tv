#!/usr/bin/env bash
# Hermetic test of mise-tasks/deploy (AC10): the app version is the highest local tag vMAJOR.MINOR.PATCH
# (mise-tasks/version highest), 0.0.1 without such a tag. The rest of deploy works as before: blocklist download, signed build, install on the TV.
# Uses the stubs from tests/stubs.sh on PATH, an adb stub and a temp git root. The release key comes from
# RELEASE_KEY_COMMAND (fake_key_command); without a key source deploy stops before the build.
# Needs only bash, git, jq, openssl and shasum. Run: bash tests/deploy-test.sh
set -uo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/deploy-test.XXXXXX") || exit 1
trap 'rm -rf "$work"' EXIT
# shellcheck source=tests/stubs.sh
source "$repo/tests/stubs.sh"
install_stubs "$work/bin" || { echo "deploy-test: install_stubs failed" >&2; exit 1; }

# adb: the TV is connected ("device"); install prints Success. Every call appends its arguments to $STUB_ADB.
cat >"$work/bin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >>"$STUB_ADB"
[[ ${1:-} == -s ]] && shift 2
case "${1:-}" in
  get-state) echo device ;;
  install) echo Success ;;
  shell) exit 0 ;;
  *) echo "adb stub: unexpected $*" >&2; exit 1 ;;
esac
EOF
chmod +x "$work/bin/adb"

cert_dir=$work/cert
mkdir -p "$cert_dir"
openssl req -new -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -keyout "$cert_dir/key.pem" -days 1 \
  -subj "/CN=deploy-test" -out "$cert_dir/cert.pem" 2>/dev/null || { echo "deploy-test: openssl failed" >&2; exit 1; }
digest=$(openssl x509 -in "$cert_dir/cert.pem" -outform DER | shasum -a 256 | cut -d' ' -f1)

# Fresh root with app/build.gradle.kts (applicationId), local.json (adb, blocklist URL), the test certificate and
# the gradlew stub; one commit on main. Arguments: the tags to set on that commit.
root=$work/root
setup() {
  rm -rf "$root" "$work/calls.log" "$work/adb.log" "$work/urls.log"
  mkdir -p "$root/app" "$root/config"
  cp "$repo/app/build.gradle.kts" "$root/app/build.gradle.kts"
  cp "$cert_dir/cert.pem" "$root/config/release-cert.pem"
  cp "$work/bin/gradlew" "$root/gradlew"
  printf '{"adb": "192.0.2.10:5555", "blocklist": "https://blocklist.test/list.txt"}\n' >"$root/local.json"
  git_in "$root" init -q && git_in "$root" add -A && git_in "$root" commit -qm init ||
    { echo "deploy-test: git setup failed" >&2; exit 1; }
  local tag
  for tag; do git_in "$root" tag "$tag" || { echo "deploy-test: tag $tag failed" >&2; exit 1; }; done
}

# The release key command; a test empties it to run deploy without a key source.
key_command=$fake_key_command
run_deploy() {
  env -u RELEASE_SIGNING_KEY -u RELEASE_KEY_COMMAND ${key_command:+RELEASE_KEY_COMMAND="$key_command"} PATH="$work/bin:$PATH" MISE_PROJECT_ROOT=$root GRADLE_ROOT=$root \
    STUB_CALLS=$work/calls.log STUB_ADB=$work/adb.log STUB_URLS=$work/urls.log STUB_BLOCKLIST=example.test \
    STUB_DIGEST=$digest bash "$repo/mise-tasks/deploy" >"$work/out.log" 2>&1
}

# The single -PappVersion= argument of the gradlew calls, empty without one.
app_version() { grep -o -- '-PappVersion=[^ ]*' "$work/calls.log" | sed 's/^-PappVersion=//'; }

# (a) no tag: version 0.0.1; the rest of deploy works as before.
setup
if run_deploy; then
  check "(a) version 0.0.1, got $(app_version)" [ "$(app_version)" = 0.0.1 ]
  check "(a) no -PversionCode" [ "$(grep -c -- -PversionCode "$work/calls.log")" = 0 ]
  check "(a) build arguments" grep -q -- '^-q acceptBlocklist assembleRelease -PreleaseSigned=true ' "$work/calls.log"
  check "(a) blocklist candidate" grep -qx 'candidate example.test' "$work/calls.log"
  check "(a) blocklist URL" grep -qx 'https://blocklist.test/list.txt' "$work/urls.log"
  check "(a) installed" grep -q '^-s 192.0.2.10:5555 install -r .*app-release.apk$' "$work/adb.log"
  check "(a) signed APK" [ -f "$root/app/build/outputs/apk/release/app-release.apk" ]
else
  fail "(a) deploy exited non-zero: $(cat "$work/out.log")"
fi

# (b) the highest SemVer tag wins, by versionCode; v1, v01.2.3, v1.2.100 and vfoo are no SemVer tag.
setup v0.9.0 v0.12.0 v0.5.3 v1 v01.2.3 v1.2.100 vfoo
if run_deploy; then
  check "(b) version 0.12.0, got $(app_version)" [ "$(app_version)" = 0.12.0 ]
  check "(b) installed" grep -q ' install -r ' "$work/adb.log"
else
  fail "(b) deploy exited non-zero: $(cat "$work/out.log")"
fi

# (c) no key source: deploy stops before the build and installs nothing.
not_installed() { ! grep -qs ' install ' "$work/adb.log"; }
setup
key_command=''
if run_deploy; then
  fail "(c) deploy exited 0 without a release key"
else
  check "(c) no Gradle call" [ ! -e "$work/calls.log" ]
  check "(c) nothing installed" not_installed
  check "(c) message names RELEASE_KEY_COMMAND" grep -qF RELEASE_KEY_COMMAND "$work/out.log"
fi
key_command=$fake_key_command

if ((failures > 0)); then
  echo "$failures check(s) failed." >&2
  exit 1
fi
echo "deploy-test: all checks passed."
