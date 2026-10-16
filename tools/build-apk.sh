#!/usr/bin/env bash
# RadioNet: сборка APK на Linux или macOS. Нужны curl и unzip.
set -euo pipefail
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="${RADIONET_BUILD_HOME:-$HOME/.radionet-build}"
SDK="$ROOT/android-sdk"; JDK="$ROOT/jdk"
case "$(uname -s)" in
  Darwin) OS=mac; JOS=mac ;;
  *) OS=linux; JOS=linux ;;
esac
case "$(uname -m)" in arm64|aarch64) ARCH=aarch64 ;; *) ARCH=x64 ;; esac
mkdir -p "$ROOT"

if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -qE 'version "(17|18|19|20|21)'; then
  if [ ! -d "$JDK" ]; then
    echo "==> Скачиваю Java 17"
    curl -fL "https://api.adoptium.net/v3/binary/latest/17/ga/$JOS/$ARCH/jdk/hotspot/normal/eclipse" -o "$ROOT/jdk.tgz"
    mkdir -p "$JDK" && tar -xzf "$ROOT/jdk.tgz" -C "$JDK" --strip-components=1 && rm "$ROOT/jdk.tgz"
  fi
  if [ -d "$JDK/Contents/Home" ]; then export JAVA_HOME="$JDK/Contents/Home"; else export JAVA_HOME="$JDK"; fi
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "==> Скачиваю инструменты Android"
  curl -fL "https://dl.google.com/android/repository/commandlinetools-$OS-11076708_latest.zip" -o "$ROOT/cmdline.zip"
  rm -rf "$ROOT/cmdline-tmp" && unzip -q "$ROOT/cmdline.zip" -d "$ROOT/cmdline-tmp"
  mkdir -p "$SDK/cmdline-tools" && rm -rf "$SDK/cmdline-tools/latest"
  mv "$ROOT/cmdline-tmp/cmdline-tools" "$SDK/cmdline-tools/latest" && rm -rf "$ROOT/cmdline.zip" "$ROOT/cmdline-tmp"
fi
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
if [ ! -d "$SDK/platforms/android-34" ] || [ ! -d "$SDK/build-tools/34.0.0" ]; then
  echo "==> Ставлю Android 14 SDK"
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true
  "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-34" "build-tools;34.0.0" "platform-tools"
fi

echo "sdk.dir=$SDK" > "$PROJECT/local.properties"
echo "==> Собираю APK"
cd "$PROJECT" && chmod +x gradlew && ./gradlew --no-daemon assembleRelease
cp app/build/outputs/apk/release/app-release.apk "$PROJECT/RadioNet.apk"
echo "ГОТОВО: $PROJECT/RadioNet.apk"
