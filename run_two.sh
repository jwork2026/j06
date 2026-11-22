#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./run.sh server &
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true' EXIT INT TERM
# Give the local server time to bind. Select START GAME in its menu.
sleep 3
./run.sh client 127.0.0.1
