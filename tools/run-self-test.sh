#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
OUT="$ROOT/.selftest"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT"   "$ROOT/app/src/main/java/dev/ezsign/writer/BlueEmulation.java"   "$ROOT/app/src/main/java/dev/ezsign/writer/Hex.java"   "$ROOT/app/src/main/java/dev/ezsign/writer/Protocol.java"   "$ROOT/app/src/main/java/dev/ezsign/writer/DeviceInfo.java"   "$ROOT/app/src/main/java/dev/ezsign/writer/PixelPacker.java"   "$ROOT/tools/SelfTest.java"
java -cp "$OUT" dev.ezsign.writer.SelfTest
rm -rf "$OUT"
