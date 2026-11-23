#!/usr/bin/env bash
# Hermetic test of mise-tasks/release-key: the key goes to RELEASE_KEY_STORE_COMMAND, RELEASE_KEY_COMMAND reads it
# back, and only then the certificate goes to config/release-cert.pem. A "secret store" here is a file in a temp dir.
# Needs only bash and openssl. Run: bash tests/release-key-test.sh
set -uo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/release-key-test.XXXXXX") || exit 1
trap 'rm -rf "$work"' EXIT
# shellcheck source=tests/stubs.sh
source "$repo/tests/stubs.sh"

root=$work/root
store=$work/store
cert=$root/config/release-cert.pem
store_command="cat >'$store'"
read_command="cat '$store' 2>/dev/null"

# Arguments: RELEASE_KEY_STORE_COMMAND, RELEASE_KEY_COMMAND (empty: unset). A fresh root and an empty store each run.
run_release_key() {
  rm -rf "$root" "$store" "$work/elsewhere"
  mkdir -p "$root"
  env -u RELEASE_KEY_STORE_COMMAND -u RELEASE_KEY_COMMAND ${1:+RELEASE_KEY_STORE_COMMAND="$1"} \
    ${2:+RELEASE_KEY_COMMAND="$2"} MISE_PROJECT_ROOT=$root bash "$repo/mise-tasks/release-key" \
    >"$work/out.log" 2>&1
}
no_cert() { [ ! -e "$cert" ]; }
# The stored key and the certificate belong together: both give the same public key.
cert_matches_key() {
  [[ -s $store && -s $cert ]] || return 1
  [[ $(tr -d '\n' <"$store" | base64 -d | openssl pkey -inform DER -pubout 2>/dev/null) == \
    "$(openssl x509 -in "$cert" -pubkey -noout 2>/dev/null)" ]]
}
key_not_in_log() { [[ -s $store ]] && ! grep -qF -- "$(tr -d '\n' <"$store")" "$work/out.log"; }

# (a) store and read back: the certificate matches the stored key; the key is not in the output.
if run_release_key "$store_command" "$read_command"; then
  check "(a) certificate matches the stored key" cert_matches_key
  check "(a) key not in the output" key_not_in_log
else
  fail "(a) release-key exited non-zero: $(cat "$work/out.log")"
fi

# (b) without RELEASE_KEY_COMMAND: store only, the certificate is written.
if run_release_key "$store_command" ""; then
  check "(b) certificate matches the stored key" cert_matches_key
else
  fail "(b) release-key exited non-zero: $(cat "$work/out.log")"
fi

# (c) RELEASE_KEY_STORE_COMMAND unset: stop, no certificate.
if run_release_key "" "$read_command"; then fail "(c) release-key exited 0 without a store command"; else
  check "(c) no certificate" no_cert
  check "(c) message names RELEASE_KEY_STORE_COMMAND" grep -qF RELEASE_KEY_STORE_COMMAND "$work/out.log"
fi

# (d) the store command fails: no certificate.
if run_release_key "cat >/dev/null; exit 1" ""; then fail "(d) release-key exited 0 with a failing store"; else
  check "(d) no certificate" no_cert
fi

# (e) the read command does not print the new key: no certificate.
if run_release_key "cat >'$work/elsewhere'" "$read_command"; then
  fail "(e) release-key exited 0 although the key does not read back"
else
  check "(e) no certificate" no_cert
fi

# (f) a key exists already (the read command prints one): stop before the store command runs.
if run_release_key "echo stored >>'$work/calls'; cat >/dev/null" "echo old-key"; then
  fail "(f) release-key exited 0 although a key exists"
else
  check "(f) no certificate" no_cert
  check "(f) store command not run" [ ! -e "$work/calls" ]
fi

# (g) the certificate exists already: stop, the certificate stays, the store command does not run.
rm -rf "$root" "$store" && mkdir -p "$root/config" && printf 'old cert\n' >"$cert"
if env -u RELEASE_KEY_COMMAND RELEASE_KEY_STORE_COMMAND="echo stored >>'$work/calls'; cat >/dev/null" \
  MISE_PROJECT_ROOT=$root bash "$repo/mise-tasks/release-key" >"$work/out.log" 2>&1; then
  fail "(g) release-key exited 0 although the certificate exists"
else
  check "(g) certificate unchanged" [ "$(cat "$cert")" = "old cert" ]
  check "(g) store command not run" [ ! -e "$work/calls" ]
fi

if ((failures)); then echo "release-key-test: $failures failure(s)" >&2; exit 1; fi
echo "release-key-test: ok"
