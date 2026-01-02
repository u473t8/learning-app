#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  create_issue_branch.sh <issue-number> [--repo <owner/name>] [--prefix <prefix>]

Creates a remote branch on GitHub for the given issue, based on the repo default branch.
USAGE
}

issue_number=""
repo=""
prefix=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      repo="${2:-}"
      if [[ -z "$repo" ]]; then
        echo "Missing value for --repo" >&2
        exit 1
      fi
      shift 2
      ;;
    --prefix)
      prefix="${2:-}"
      if [[ -z "$prefix" ]]; then
        echo "Missing value for --prefix" >&2
        exit 1
      fi
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ -z "$issue_number" ]]; then
        issue_number="$1"
      else
        usage >&2
        exit 1
      fi
      shift
      ;;
  esac
 done

if [[ -z "$issue_number" ]]; then
  usage >&2
  exit 1
fi

if [[ -z "$repo" ]]; then
  repo=$(gh repo view --json nameWithOwner -q .nameWithOwner)
fi

default_branch=$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name)
issue_title=$(gh issue view "$issue_number" --json title -q .title)

slug=$(python3 - <<'PY'
import re
import sys

title = sys.argv[1]
slug = re.sub(r"[^a-zA-Z0-9]+", "-", title).strip("-").lower()
print(slug or "issue")
PY
"$issue_title")

if [[ -n "$prefix" ]]; then
  branch_name="${prefix}${issue_number}-${slug}"
else
  branch_name="${issue_number}-${slug}"
fi

base_sha=$(gh api "repos/${repo}/git/ref/heads/${default_branch}" -q .object.sha)

set +e
create_out=$(gh api "repos/${repo}/git/refs" -f ref="refs/heads/${branch_name}" -f sha="$base_sha" 2>&1)
status=$?
set -e

if [[ $status -ne 0 ]]; then
  if echo "$create_out" | rg -q "Reference already exists"; then
    echo "$branch_name"
    exit 0
  fi
  echo "$create_out" >&2
  exit $status
fi

echo "$branch_name"
