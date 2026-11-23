#!/usr/bin/env bash
# Hermetic test of mise-tasks/sign with both key sources: RELEASE_SIGNING_KEY (CI) and RELEASE_KEY_COMMAND (a local
# secret store), and of sign --check. Uses the apksigner stub from tests/stubs.sh and a throwaway certificate.
# Needs only bash and openssl. Run: bash tests/sign-test.sh
set -uo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
sign=$repo/mise-tasks/sign
work=$(mktemp -d "${TMPDIR:-/tmp}/sign-test.XXXXXX") || exit 1
trap 'rm -rf "$work"' EXIT
# shellcheck source=tests/stubs.sh
source "$repo/tests/stubs.sh"
install_stubs "$work/bin" || { echo "sign-test: install_stubs failed" >&2; exit 1; }

# Key commands for failures: one prints no base64, one fails, one must never run (it leaves a mark).
broken_command="echo 'not base64 !!'"
failing_command="exit 3"
forbidden_command="echo called >>'$work/forbidden.log'; exit 1"

# A throwaway certificate; the apksigner stub reports it as the signer unless a test passes another digest.
root=$work/root
mkdir -p "$root/config"
openssl req -new -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -keyout "$work/key.pem" -days 1 \
  -subj "/CN=sign-test" -out "$root/config/release-cert.pem" 2>/dev/null ||
  { echo "sign-test: openssl failed" >&2; exit 1; }
digest=$(openssl x509 -in "$root/config/release-cert.pem" -outform DER | shasum -a 256 | cut -d' ' -f1)
other=$(printf 'other' | shasum -a 256 | cut -d' ' -f1)
printf 'unsigned apk\n' >"$work/in.apk"
good_key=$(printf 'fake key\n' | base64)

# Arguments: RELEASE_KEY_COMMAND (empty: unset), signer digest, then the sign arguments (default: in.apk out.apk).
# RELEASE_SIGNING_KEY passes through when the caller sets it. The output path is empty before each run.
run_sign() {
  local command=$1 signer=${2:-$digest}
  shift 2 2>/dev/null || shift $#
  (($#)) || set -- "$work/in.apk" "$work/out.apk"
  rm -f "$work/out.apk"
  env -u RELEASE_KEY_COMMAND ${command:+RELEASE_KEY_COMMAND="$command"} MISE_PROJECT_ROOT=$root \
    APKSIGNER=$work/bin/apksigner STUB_DIGEST=$signer bash "$sign" "$@" >"$work/out.log" 2>&1
}
signed() { cmp -s "$work/in.apk" "$work/out.apk"; }
no_apk() { [ ! -e "$work/out.apk" ]; }
log_has() { grep -qF -- "$1" "$work/out.log"; }
no_key_in_log() { ! log_has "$good_key" && ! log_has "fake key"; }
not_called() { [ ! -e "$work/forbidden.log" ]; }

unset RELEASE_SIGNING_KEY

# Key source RELEASE_SIGNING_KEY. RELEASE_KEY_COMMAND must not run while RELEASE_SIGNING_KEY is set.
# (a) valid key: signed APK, the key is not in the log.
if RELEASE_SIGNING_KEY=$good_key run_sign "$forbidden_command"; then
  check "(a) signed APK" signed
  check "(a) key not in the log" no_key_in_log
else
  fail "(a) sign with RELEASE_SIGNING_KEY exited non-zero: $(cat "$work/out.log")"
fi
# (b) set but empty: stop without APK, RELEASE_KEY_COMMAND is not the fallback.
if RELEASE_SIGNING_KEY='' run_sign "$fake_key_command"; then
  fail "(b) sign exited 0 with an empty RELEASE_SIGNING_KEY"
else
  check "(b) no APK" no_apk
  check "(b) message names RELEASE_SIGNING_KEY" log_has RELEASE_SIGNING_KEY
fi
# (c) no base64 key: stop without APK.
if RELEASE_SIGNING_KEY='not base64 !!' run_sign "$forbidden_command"; then
  fail "(c) sign exited 0 with a broken RELEASE_SIGNING_KEY"
else
  check "(c) no APK" no_apk
fi
# (d) a valid base64 key, but not the release key: stop without APK.
if RELEASE_SIGNING_KEY=$(printf 'wrong key' | base64) run_sign "$forbidden_command"; then
  fail "(d) sign exited 0 with the wrong key"
else
  check "(d) no APK" no_apk
fi
# (e) signer is not release-cert.pem: the signed APK is removed.
if RELEASE_SIGNING_KEY=$good_key run_sign "$forbidden_command" "$other"; then
  fail "(e) sign exited 0 although the signer is another certificate"
else
  check "(e) APK removed" no_apk
  check "(e) key not in the log" no_key_in_log
fi
check "(a-e) RELEASE_KEY_COMMAND never ran" not_called

# Key source RELEASE_KEY_COMMAND (RELEASE_SIGNING_KEY unset).
# (f) valid key from the command: signed APK.
if run_sign "$fake_key_command"; then
  check "(f) signed APK" signed
  check "(f) key not in the log" no_key_in_log
else
  fail "(f) sign with RELEASE_KEY_COMMAND exited non-zero: $(cat "$work/out.log")"
fi
# (g) no key source at all: stop without APK, the message names both variables.
if run_sign ""; then
  fail "(g) sign exited 0 without a key source"
else
  check "(g) no APK" no_apk
  check "(g) message names RELEASE_KEY_COMMAND" log_has RELEASE_KEY_COMMAND
  check "(g) message names RELEASE_SIGNING_KEY" log_has RELEASE_SIGNING_KEY
fi
# (h) the command prints no base64 key: stop without APK.
if run_sign "$broken_command"; then fail "(h) sign exited 0 with a broken key"; else check "(h) no APK" no_apk; fi
# (i) the command fails: stop without APK.
if run_sign "$failing_command"; then fail "(i) sign exited 0 with a failing command"; else check "(i) no APK" no_apk; fi
# (j) signer is not release-cert.pem: the signed APK is removed.
if run_sign "$fake_key_command" "$other"; then
  fail "(j) sign exited 0 although the signer is another certificate"
else
  check "(j) APK removed" no_apk
fi

# sign --check: reads the key, writes no APK.
# (k) a key source works: exit 0, no APK, no key in the log.
if run_sign "$fake_key_command" "$digest" --check; then
  check "(k) no APK" no_apk
  check "(k) key not in the log" no_key_in_log
else
  fail "(k) sign --check exited non-zero: $(cat "$work/out.log")"
fi
# (l) no key source: non-zero.
if run_sign "" "$digest" --check; then fail "(l) sign --check exited 0 without a key source"; fi
# (m) the command fails: non-zero.
if run_sign "$failing_command" "$digest" --check; then fail "(m) sign --check exited 0 with a failing command"; fi

if ((failures)); then echo "sign-test: $failures failure(s)" >&2; exit 1; fi
echo "sign-test: ok"
