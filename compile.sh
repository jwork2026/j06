#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f lib/lwjgl/lwjgl-3.3.6.jar ]]; then
  echo 'Run ./download_lwjgl.sh first.' >&2; exit 1
fi
mkdir -p build/classes
find src/main/java -name '*.java' -print | sort > build/sources.txt
javac --release 11 -encoding UTF-8 -cp 'lib/lwjgl/*' -d build/classes @build/sources.txt
