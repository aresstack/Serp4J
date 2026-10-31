#!/usr/bin/env bash
# Prototyp (Spike): kompiliert mit --release 8 und führt die Demo aus. Kein Teil des Gradle-Builds.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac --release 8 -Xlint:all -d out src/*.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out Demo .
