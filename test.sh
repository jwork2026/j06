#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./compile.sh
mkdir -p build/test-classes
find src/test/java -name '*.java' -print | sort > build/test-sources.txt
javac --release 11 -encoding UTF-8 -cp 'build/classes:lib/lwjgl/*' -d build/test-classes @build/test-sources.txt
java -Djava.awt.headless=true -cp 'build/test-classes:build/classes:lib/lwjgl/*' com.gameengine.IntegrationChecks
