#!/usr/bin/env bash
#
# Runs every package's tests and reports a total.
#
# Two things this handles that a naive loop does not.
#
# Piping `swift test` into a filter discards its exit code, so a package that fails to *compile*
# prints nothing and looks like it passed. Each package's output is captured and checked for the
# success line instead.
#
# Adding a type to a `.package(path:)` dependency does not reliably invalidate its dependents'
# incremental state, so they fail with `cannot find type X in scope` pointing at the file that
# plainly declares X. That has happened every time SimplicityServices gained a service. When it is
# detected, the package's .build is cleared and the run retried once.

set -uo pipefail

cd "$(dirname "$0")"
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

total=0
failed=0

run_package() {
  (cd "$1" && swift test 2>&1)
}

for package in Packages/*/; do
  name=$(basename "$package")
  output=$(run_package "$package")

  if grep -q "cannot find type .* in scope" <<<"$output"; then
    printf '%-24s stale build state, clearing and retrying\n' "$name"
    rm -rf "${package}.build"
    output=$(run_package "$package")
  fi

  line=$(grep -oE 'Test run with [0-9]+ tests? in [0-9]+ suites? passed' <<<"$output" | tail -1)

  if [ -n "$line" ]; then
    count=$(grep -oE '[0-9]+' <<<"$line" | head -1)
    total=$((total + count))
    printf '%-24s %s\n' "$name" "$line"
  else
    printf '%-24s FAILED\n' "$name"
    grep -E 'error:|✘' <<<"$output" | head -5
    failed=1
  fi
done

echo "---"
echo "$total tests"

if ! swiftlint --strict >/dev/null 2>&1; then
  echo "SwiftLint found violations:"
  swiftlint --strict 2>&1 | grep 'error:' | sed 's|.*/ios/||' | head -20
  failed=1
else
  echo "SwiftLint clean"
fi

exit "$failed"
