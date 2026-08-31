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

# --no-parallel because the suites share one process-wide Factory container.
#
# Every view-model suite registers its mocks into Container.shared, and `.serialized` only orders
# tests *within* a suite — Swift Testing still runs different suites at the same time. One suite
# replacing another's sessionService mid-test makes the model resolve a session with no
# organisation, so a guard returns early and the test sees nothing happen at all.
#
# That surfaced as InvitationsViewModelTests failing on CI while passing locally, which is the
# shape this bug will always take: it depends on core count and interleaving. The suites run in
# well under a second, so serialising them costs nothing worth measuring.
run_package() {
  (cd "$1" && swift test --no-parallel 2>&1)
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

# "not installed" and "found violations" are different answers, and conflating them produced a CI
# failure that said "SwiftLint found violations" and then listed none.
expected_lint_version=$(cat .swiftlint-version)

if ! command -v swiftlint >/dev/null 2>&1; then
  echo "SwiftLint is not installed — skipping. Install it with: brew install swiftlint"
elif [ "$(swiftlint version)" != "$expected_lint_version" ]; then
  # Not a failure — a local mismatch should not block someone's work — but loud, because the
  # answer genuinely differs between versions and CI uses the pinned one.
  echo "SwiftLint $(swiftlint version) differs from the pinned $expected_lint_version;" \
       "CI will use the pinned version. Skipping."
elif swiftlint --strict >/dev/null 2>&1; then
  echo "SwiftLint clean"
else
  echo "SwiftLint found violations:"
  swiftlint --strict 2>&1 | grep 'error:' | sed 's|.*/ios/||' | head -20
  failed=1
fi

exit "$failed"
