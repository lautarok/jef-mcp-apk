#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-$HOME/Android}"
PLATFORM_JAR="$SDK_ROOT/platforms/android-34/android.jar"
BUILD_TOOLS="$SDK_ROOT/build-tools/34.0.0"

# Busca kotlin-stdlib.jar en ubicaciones típicas de Linux Mint / SDKMAN
KOTLIN_STDLIB=""
for candidate in \
  "/usr/share/kotlin/lib/kotlin-stdlib.jar" \
  "/usr/lib/kotlin/lib/kotlin-stdlib.jar" \
  "$HOME/.sdkman/candidates/kotlin/current/lib/kotlin-stdlib.jar"
do
  if [ -f "$candidate" ]; then
    KOTLIN_STDLIB="$candidate"
    break
  fi
done

if [ -z "$KOTLIN_STDLIB" ]; then
  KOTLIN_STDLIB="$(find /usr -name "kotlin-stdlib.jar" 2>/dev/null | head -n 1 || true)"
fi

if [ -z "$KOTLIN_STDLIB" ]; then
  echo "Error: no se encontró kotlin-stdlib.jar" >&2
  exit 1
fi

OUT="build"
KEYSTORE="$OUT/debug.keystore"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "1. Compilando recursos y manifest con aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir res -o "$OUT/compiled_res.zip"
"$BUILD_TOOLS/aapt2" link \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  -o "$OUT/base.apk" \
  -I "$PLATFORM_JAR" \
  --manifest AndroidManifest.xml \
  --java "$OUT/gen" \
  -A assets \
  "$OUT/compiled_res.zip"

echo "2. Compilando Kotlin..."
find src "$OUT/gen" -name "*.kt" -o -name "*.java" > "$OUT/sources.txt"
kotlinc @"$OUT/sources.txt" \
  -classpath "$PLATFORM_JAR:$KOTLIN_STDLIB" \
  -d "$OUT/classes"

echo "3. Convirtiendo bytecode a DEX con d8..."
find "$OUT/classes" -name "*.class" > "$OUT/classes.txt"
"$BUILD_TOOLS/d8" \
  --lib "$PLATFORM_JAR" \
  --output "$OUT/dex" \
  @"$OUT/classes.txt" \
  "$KOTLIN_STDLIB"

echo "4. Empaquetando APK sin firmar..."
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "../unsigned.apk" classes*.dex)

echo "5. Alineando y firmando APK..."
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$OUT/jef-mcp.apk" \
  "$OUT/aligned.apk"

echo "Listo: $OUT/jef-mcp.apk"