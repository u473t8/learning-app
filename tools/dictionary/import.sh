#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/dictionary-import.jar"
INPUT_DIR_DEFAULT="/var/lib/learning-app/dictionary"

input_dir_set=false
for arg in "$@"; do
  if [[ "$arg" == "--input-dir" ]]; then
    input_dir_set=true
    break
  fi
done

extra_args=()
if [[ "$input_dir_set" == "false" ]]; then
  extra_args+=("--input-dir" "${INPUT_DIR_DEFAULT}")
fi

exec java -jar "${JAR_PATH}" "${extra_args[@]}" "$@"
