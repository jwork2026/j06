#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
version=3.3.6
case "$(uname -s)" in
  Darwin) platform=macos;; Linux) platform=linux;; MINGW*|MSYS*|CYGWIN*) platform=windows;;
  *) echo 'Unsupported OS'; exit 1;;
esac
case "$(uname -m)" in
  arm64|aarch64) classifier="$platform-arm64";;
  x86_64|amd64) classifier="$platform";;
  *) echo 'Unsupported architecture'; exit 1;;
esac
mkdir -p lib/lwjgl
for module in lwjgl lwjgl-glfw lwjgl-opengl; do
  for suffix in '' "-natives-$classifier"; do
    file="$module-$version$suffix.jar"
    curl --fail --location --retry 2 --connect-timeout 15 --max-time 120 \
      "https://repo.maven.apache.org/maven2/org/lwjgl/$module/$version/$file" \
      -o "lib/lwjgl/$file.part"
    mv "lib/lwjgl/$file.part" "lib/lwjgl/$file"
  done
done
echo 'LWJGL ready (native libraries load directly from JARs).'
