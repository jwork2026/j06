#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./compile.sh
mode="${1:-server}"
if (( $# )); then shift; fi
case "$mode" in
  server) main=com.gameengine.example.Game;;
  client) main=com.gameengine.example.ClientLauncher;;
  replay) main=com.gameengine.example.ReplayLauncher;;
  benchmark) main=com.gameengine.example.ParallelBenchmark;;
  *) echo 'Usage: ./run.sh [server|client [host]|replay [file]|benchmark [objects] [steps]]'; exit 1;;
esac
flags=("-Dgame.parallel=${GAME_PARALLEL:-true}")
if [[ "$(uname -s)" == Darwin && "$mode" != benchmark ]]; then flags+=(-XstartOnFirstThread); fi
exec java "${flags[@]}" -cp 'build/classes:lib/lwjgl/*' "$main" "$@"
