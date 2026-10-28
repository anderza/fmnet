#!/usr/bin/env bash
# Собирает RadioNet для Windows (x64) в dist/RadioNet-windows.zip без Windows и без Node.js:
# берёт официальную сборку Electron, кладёт в неё плеер из app/src/main/assets и переименовывает exe.
set -euo pipefail
EV=33.4.11
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/dist"; WORK="$OUT/win-build"; APP="$WORK/RadioNet"
mkdir -p "$OUT"
ZIP="$OUT/electron-v$EV-win32-x64.zip"
if [ ! -f "$ZIP" ]; then
  curl -fsSL -o "$ZIP" "https://github.com/electron/electron/releases/download/v$EV/electron-v$EV-win32-x64.zip"
  curl -fsSL -o "$OUT/SHASUMS256.txt" "https://github.com/electron/electron/releases/download/v$EV/SHASUMS256.txt"
  (cd "$OUT" && grep " \*electron-v$EV-win32-x64.zip" SHASUMS256.txt | sed 's/ \*/  /' | sha256sum -c -)
fi
rm -rf "$WORK"; mkdir -p "$APP"
unzip -q "$ZIP" -d "$APP"
mv "$APP/electron.exe" "$APP/RadioNet.exe"
rm -f "$APP/resources/default_app.asar"
mkdir -p "$APP/resources/app"
cp "$ROOT/windows/main.js" "$ROOT/windows/package.json" "$APP/resources/app/"
cp "$ROOT/app/src/main/assets/"* "$APP/resources/app/"
cp "$ROOT/icon-512.png" "$APP/resources/app/icon.png"
cp "$ROOT/windows/README-WINDOWS.txt" "$APP/"
(cd "$WORK" && rm -f "$OUT/RadioNet-windows.zip" && zip -qr -9 "$OUT/RadioNet-windows.zip" RadioNet)
echo "Готово: $OUT/RadioNet-windows.zip"
