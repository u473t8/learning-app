#!/usr/bin/env bash
set -euo pipefail

branch=$(git rev-parse --abbrev-ref HEAD)
issue_number=$(printf "%s" "$branch" | sed -n 's/^\([0-9][0-9]*\).*/\1/p')

if [[ -z "$issue_number" ]]; then
  echo "No issue number found in branch '$branch'. Expected a branch starting with digits, e.g. 38-migrate..." >&2
  exit 1
fi

echo "$issue_number"
