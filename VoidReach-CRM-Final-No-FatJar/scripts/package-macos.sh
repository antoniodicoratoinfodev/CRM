#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_DIR="$ROOT_DIR/target/jpackage-input"
OUTPUT_DIR="$ROOT_DIR/target/packages/macos"
APP_JAR="CRMApp-1.0-SNAPSHOT.jar"

command -v mvn >/dev/null || { echo "mvn was not found on PATH. Install Apache Maven 3.9+." >&2; exit 1; }
command -v jpackage >/dev/null || { echo "jpackage was not found on PATH. Install a JDK 26+ and make sure its 'bin' directory is on PATH." >&2; exit 1; }

cd "$ROOT_DIR"
rm -rf "$INPUT_DIR" "$OUTPUT_DIR"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR"

mvn -q package -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$INPUT_DIR"
cp "target/$APP_JAR" "$INPUT_DIR/$APP_JAR"

jpackage \
  --type app-image \
  --name VoidReach \
  --app-version 1.0 \
  --input "$INPUT_DIR" \
  --main-jar "$APP_JAR" \
  --main-class com.crm.app.AppLauncher \
  --icon src/main/packaging/macos/VoidReach-v2.icns \
  --java-options --enable-native-access=ALL-UNNAMED \
  --dest "$OUTPUT_DIR"
