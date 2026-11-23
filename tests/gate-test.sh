#!/usr/bin/env bash
# Test of the gate mise-tasks/test: a red Gradle run or a red shell test makes the gate red, and it stops there.
# Runs mise-tasks/test in a temp root with a gradlew stub and two shell tests. Needs only bash.
# Run: bash tests/gate-test.sh
set -uo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/gate-test.XXXXXX") || exit 1
trap 'rm -rf "$work"' EXIT
# shellcheck source=tests/stubs.sh
source "$repo/tests/stubs.sh"

# The temp root: gradlew fails for the call that names $RED (check or test) and logs every call; tests/a-test.sh
# fails when RED=a; tests/b-test.sh logs that it ran.
mkdir -p "$work/root/tests"
cat >"$work/root/gradlew" <<'STUB'
#!/usr/bin/env bash
echo "gradlew $*" >>"$GATE_LOG"
for arg in "$@"; do [[ $arg == "$RED" ]] && exit 1; done
exit 0
STUB
chmod +x "$work/root/gradlew"
printf '[[ $RED == a ]] && exit 1\nexit 0\n' >"$work/root/tests/a-test.sh"
printf 'echo b-test >>"$GATE_LOG"\n' >"$work/root/tests/b-test.sh"

# Runs the gate with RED=$1; returns its exit code.
gate() {
  rm -f "$work/gate.log"
  RED=$1 GATE_LOG=$work/gate.log MISE_PROJECT_ROOT=$work/root bash "$repo/mise-tasks/test" >/dev/null 2>&1
}
ran() { grep -qF -- "$1" "$work/gate.log"; }

check "(gate a) all green: exit 0" gate none
check "(gate a) all green: build-logic tests ran" ran "-p build-logic test"
check "(gate a) all green: shell tests ran" ran b-test

if gate check; then fail "(gate b) red ./gradlew check: exit 0"; fi
check "(gate b) red check: no build-logic tests after it" bash -c "! grep -qF -- '-p build-logic' '$work/gate.log'"
check "(gate b) red check: no shell tests after it" bash -c "! grep -qF b-test '$work/gate.log'"

if gate test; then fail "(gate c) red build-logic tests: exit 0"; fi
check "(gate c) red build-logic tests: no shell tests after it" bash -c "! grep -qF b-test '$work/gate.log'"

if gate a; then fail "(gate d) red shell test: exit 0"; fi
check "(gate d) red shell test: stops before the next one" bash -c "! grep -qF b-test '$work/gate.log'"

if ((failures)); then echo "gate-test: $failures failure(s)" >&2; exit 1; fi
echo "gate-test: ok"
