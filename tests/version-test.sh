#!/usr/bin/env bash
# Hermetic test of mise-tasks/version (AC10): a temp project root (a git repo with tags and a fake origin/main).
# Needs only bash and git. Run: bash tests/version-test.sh
set -uo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
version=$repo/mise-tasks/version
work=$(mktemp -d "${TMPDIR:-/tmp}/version-test.XXXXXX") || exit 1
trap 'rm -rf "$work"' EXIT
# shellcheck source=tests/stubs.sh
source "$repo/tests/stubs.sh"

root=$work/root
git_root() { git_in "$root" "$@"; }

# Fresh root: one commit on main, origin/main on that commit, no tag.
setup() {
  rm -rf "$root"
  mkdir -p "$root"
  git_root init -q && git_root commit -q --allow-empty -m fixture
  git_root update-ref refs/remotes/origin/main HEAD
}

run_version() { MISE_PROJECT_ROOT=$root bash "$version" "$@" >"$work/out.log" 2>"$work/err.log"; }
out() { cat "$work/out.log"; }

# Expects version <args…> to exit 0.
accepts() {
  local what=$1; shift
  run_version "$@" || fail "$what: version $* exited non-zero: $(cat "$work/err.log")"
}

# Expects version check <tag> to stop with a message that contains $2.
check_rejects() {
  local what=$1 message=$2 tag=$3
  if run_version check "$tag"; then
    fail "$what: check $tag exited 0"
  else
    check "$what: message \"$message\" in: $(cat "$work/err.log")" grep -qF -- "$message" "$work/err.log"
  fi
}

# (a) highest without any tag: 0.0.1 (versionCode 1).
setup
accepts "(a)" highest
check "(a) highest without tag is 0.0.1, got $(out)" [ "$(out)" = 0.0.1 ]

# (b) highest takes the version with the highest versionCode; tags that AppVersion rejects do not count:
# v1, v01.2.3, v1.02.3, v1.2.100, v1.2.3x, 1.9.9 (no v), v0.0.0 (code 0), v210001.0.0 (code above 2100000000).
setup
for t in v0.1.0 v0.10.0 v0.9.99 v1 v01.2.3 v1.02.3 v1.2.100 v1.2.3x 1.9.9 v0.0.0 v210001.0.0; do git_root tag "$t"; done
accepts "(b)" highest
check "(b) highest is 0.10.0, got $(out)" [ "$(out)" = 0.10.0 ]
git_root tag v2.0.0
accepts "(b) major" highest
check "(b) highest is 2.0.0, got $(out)" [ "$(out)" = 2.0.0 ]

# (c) check accepts the first tag v0.1.0 on a commit of main and prints MAJOR.MINOR.PATCH; annotated tags work too.
setup
git_root tag v0.1.0
accepts "(c)" check v0.1.0
check "(c) check prints 0.1.0, got $(out)" [ "$(out)" = 0.1.0 ]
git_root tag -a -m release v0.2.0
accepts "(c) annotated" check v0.2.0
check "(c) check prints 0.2.0, got $(out)" [ "$(out)" = 0.2.0 ]

# (d) the tag format: vMAJOR.MINOR.PATCH without leading zeros, MINOR and PATCH at most 99.
setup
for t in 1.2.3 v1 v1.2 v1.2.3.4 v01.2.3 v1.02.3 v1.2.03 v1.100.0 v1.2.100 V1.2.3 v1.2.3-rc1 "v 1.2.3"; do
  git_root tag "$t" 2>/dev/null
  check_rejects "(d) format $t" "vMAJOR.MINOR.PATCH" "$t"
done
check_rejects "(d) empty tag" "vMAJOR.MINOR.PATCH" ""

# (e) the versionCode must be 1 to 2100000000; v210000.0.0 is exactly the upper limit.
setup
for t in v0.0.0 v210000.0.1 v9999999.0.0; do
  git_root tag "$t"
  check_rejects "(e) range $t" 2100000000 "$t"
done
setup
git_root tag v210000.0.0
accepts "(e) upper bound" check v210000.0.0

# (f) the tag must be greater than every other SemVer tag, compared by versionCode, not as text.
setup
git_root tag v0.10.0
git_root tag v0.9.0
check_rejects "(f) lower than v0.10.0" v0.10.0 v0.9.0
setup
git_root tag v1.0.0
git_root tag v1.0.0-rc1
git_root tag v0.99.99
check_rejects "(f) lower than v1.0.0" v1.0.0 v0.99.99
setup
git_root tag v0.9.0
git_root tag v1.2
git_root tag v0.10.0
accepts "(f) v0.10.0 above v0.9.0" check v0.10.0

# (g) the tagged commit must be an ancestor of origin/main.
setup
git_root checkout -qb side
git_root commit -q --allow-empty -m side
git_root tag v0.2.0
check_rejects "(g) not on main" origin/main v0.2.0
git_root update-ref -d refs/remotes/origin/main
git_root checkout -q main
git_root tag v0.3.0
check_rejects "(g) origin/main missing" origin/main v0.3.0

# (h) the tag must exist in the checkout.
setup
check_rejects "(h) unknown tag" v0.2.0 v0.2.0

# (i) wrong usage: no command, an unknown command, check without tag, highest with an argument.
setup
for args in "" "foo" "check" "highest v0.1.0"; do
  # shellcheck disable=SC2086
  if run_version $args; then fail "(i) version $args exited 0"; fi
done

if ((failures)); then
  echo "version-test: $failures failure(s)." >&2
  exit 1
fi
echo "version-test: ok"
