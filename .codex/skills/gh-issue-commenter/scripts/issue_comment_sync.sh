#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  issue_comment_sync.sh <issue-number> <body-file> [--commenter <login>]

Updates a single Codex-owned comment (identified by marker) or creates it.
USAGE
}

issue_number=""
body_file=""
commenter_login=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --commenter)
      commenter_login="${2:-}"
      if [[ -z "$commenter_login" ]]; then
        echo "Missing value for --commenter" >&2
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
      elif [[ -z "$body_file" ]]; then
        body_file="$1"
      else
        usage >&2
        exit 1
      fi
      shift
      ;;
  esac
done

if [[ -z "$issue_number" || -z "$body_file" ]]; then
  usage >&2
  exit 1
fi

if [[ ! -f "$body_file" ]]; then
  echo "Body file not found: $body_file" >&2
  exit 1
fi

marker="<!-- CODEX:gh-issue-commenter -->"

repo=$(gh repo view --json nameWithOwner -q .nameWithOwner)
if [[ -n "$commenter_login" ]]; then
  login="$commenter_login"
else
  login=$(gh api user -q .login)
fi

comment_id=$(gh api "repos/${repo}/issues/${issue_number}/comments" --paginate | \
  python3 - <<'PY'
import json
import sys

marker = "<!-- CODEX:gh-issue-commenter -->"
login = sys.argv[1]

for line in sys.stdin:
    for item in json.loads(line):
        user = item.get("user", {}).get("login")
        body = item.get("body", "")
        if user == login and marker in body:
            print(item.get("id"))
            raise SystemExit(0)
PY
"$login")

if [[ -n "$comment_id" ]]; then
  gh api "repos/${repo}/issues/comments/${comment_id}" \
    -f body="$(cat "$body_file")"
else
  gh issue comment "$issue_number" --body-file "$body_file"
fi
