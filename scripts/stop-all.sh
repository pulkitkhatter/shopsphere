#!/usr/bin/env bash
cd "$(dirname "$0")/.."
for f in .pids/*.pid; do
  [ -e "$f" ] || continue
  pid=$(cat "$f"); name=$(basename "$f" .pid)
  if kill -0 "$pid" 2>/dev/null; then kill "$pid" && echo "stopped $name"; fi
  rm -f "$f"
done
[ "${1:-}" = "--infra" ] && docker compose down
exit 0
