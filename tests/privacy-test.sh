#!/usr/bin/env bash
# Test of AC16: no private content in the tracked files. Runs the git grep of the spec on the repo; it must find
# nothing. Needs only bash and git. Run: bash tests/privacy-test.sh
set -uo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
# shellcheck source=tests/stubs.sh
source "$repo/tests/stubs.sh"

# The search words of AC16. Each word is split in two quoted parts, so the search does not find this file.
words=(hu"hu" keller"kino" fil"mo" serien"stream" ani"world" hd"filme" tv".pr0gramm.dev" pan"golin" pod"man")
args=()
for word in "${words[@]}"; do args+=(-e "$word"); done

# git grep exits 0 with a match, 1 without one, and higher on an error.
found=$(git -C "$repo" grep -n -i "${args[@]}")
case $? in
  1) ;;
  0) fail "private content in tracked files:"$'\n'"$found" ;;
  *) fail "git grep failed" ;;
esac

# No real e-mail address: every address in a tracked text file must use a reserved example domain (RFC 2606:
# example.org, example.com, example.net, .invalid, .test). Kotlin labels like this@Outer.member are no address.
addresses=$(git -C "$repo" grep -nIoE '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+')
case $? in 0 | 1) ;; *) fail "git grep for e-mail addresses failed" ;; esac
real=$(grep -vE '@([A-Za-z0-9-]+\.)*(example\.(org|com|net)|invalid|test)$|:this@' <<<"$addresses" | grep -v '^$')
[[ -z $real ]] || fail "e-mail addresses outside the example domains:"$'\n'"$real"

if ((failures)); then
  echo "privacy-test: $failures failure(s)." >&2
  exit 1
fi
echo "privacy-test: ok"
