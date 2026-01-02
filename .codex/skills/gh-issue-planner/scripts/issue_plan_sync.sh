#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  issue_plan_sync.sh show
  issue_plan_sync.sh update <plan-file|-> [--replace-body]

Notes:
  - Expects branch name to start with digits (e.g., 38-my-branch)
  - Requires gh CLI and authentication
EOF
}

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required. Install it from https://cli.github.com/ and retry." >&2
  exit 1
fi

cmd="${1:-}"
if [[ -z "$cmd" ]]; then
  usage >&2
  exit 1
fi

branch=$(git rev-parse --abbrev-ref HEAD)
issue_number=$(printf "%s" "$branch" | sed -n 's/^\([0-9][0-9]*\).*/\1/p')
if [[ -z "$issue_number" ]]; then
  echo "No issue number found in branch '$branch'. Expected a branch starting with digits, e.g. 38-migrate..." >&2
  exit 1
fi

case "$cmd" in
  show)
    gh issue view "$issue_number" --json url,body -q '.url + "\n\n" + .body'
    ;;
  update)
    plan_path="${2:-}"
    if [[ -z "$plan_path" ]]; then
      usage >&2
      exit 1
    fi

    replace_body=false
    if [[ "${3:-}" == "--replace-body" ]]; then
      replace_body=true
    fi

    temp_plan=""
    if [[ "$plan_path" == "-" ]]; then
      temp_plan=$(mktemp)
      cat >"$temp_plan"
      plan_path="$temp_plan"
    fi

    if [[ ! -f "$plan_path" ]]; then
      echo "Plan file not found: $plan_path" >&2
      exit 1
    fi

    plan_text=$(cat "$plan_path")
    if [[ -z "${plan_text//[[:space:]]/}" ]]; then
      echo "Plan content is empty." >&2
      exit 1
    fi

    if [[ "$replace_body" == true ]]; then
      new_body="$plan_text"
    else
      body=$(gh issue view "$issue_number" --json body -q .body)
      new_body=$(python3 - "$plan_path" <<'PY'
import re
import sys
from pathlib import Path

plan_path = sys.argv[1]
plan = Path(plan_path).read_text()
body = sys.stdin.read()

start = "<!-- PLAN:START -->"
end = "<!-- PLAN:END -->"
block = f"{start}\n{plan.rstrip()}\n{end}"

if start in body and end in body:
    pattern = re.compile(re.escape(start) + r".*?" + re.escape(end), re.S)
    new_body = pattern.sub(block, body)
else:
    if body.strip():
        new_body = body.rstrip() + "\n\n" + block + "\n"
    else:
        new_body = block + "\n"

sys.stdout.write(new_body)
PY
)
    fi

    tmp_body=$(mktemp)
    printf "%s" "$new_body" >"$tmp_body"
    gh issue edit "$issue_number" --body-file "$tmp_body"
    rm -f "$tmp_body"
    if [[ -n "$temp_plan" ]]; then
      rm -f "$temp_plan"
    fi
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
