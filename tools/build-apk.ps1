# RadioNet: сборка APK на Windows. Запускается из build-apk.bat.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Project = Split-Path -Parent $PSScriptRoot
$Root    = 'C:\RadioNetBuild'          # путь без пробелов и русских букв: так Android-инструменты работают надёжнее
$Jdk     = "$Root\jdk"
$Sdk     = "$Root\android-sdk"
$Work    = "$Root\src"
$JdkUrl  = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
$SdkUrl  = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'

function Step($text) { Write-Host ''; Write-Host "==> $text" -ForegroundColor Cyan }
function Fail($text) { Write-Host ''; Write-Host "ОШИБКА: $text" -ForegroundColor Red; exit 1 }
function Download($url, $file) {
  for ($i = 1; $i -le 3; $i++) {
    try { Invoke-WebRequest -Uri $url -OutFile $file -UseBasicParsing; return } catch {
      if ($i -eq 3) { Fail "не удалось скачать $url. Проверьте интернет и запустите ещё раз." }
      Start-Sleep -Seconds 3 }
  }
}

Write-Host 'RadioNet — сборка APK для Android и Android TV' -ForegroundColor Yellow
Write-Host "Инструменты будут лежать в $Root (около 1,5 ГБ). Первый запуск занимает 10–20 минут, следующие — 1–3 минуты."
New-Item -ItemType Directory -Force $Root | Out-Null

# 1. Java 17
$java = Get-ChildItem $Jdk -Recurse -Filter java.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $java) {
  Step 'Скачиваю Java 17 (около 190 МБ)'
  $zip = "$Root\jdk.zip"; Download $JdkUrl $zip
  Remove-Item $Jdk -Recurse -Force -ErrorAction SilentlyContinue
  Expand-Archive $zip $Jdk -Force; Remove-Item $zip -Force
  $java = Get-ChildItem $Jdk -Recurse -Filter java.exe | Select-Object -First 1
  if (-not $java) { Fail 'Java не распаковалась.' }
}
$env:JAVA_HOME = Split-Path (Split-Path $java.FullName)
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue

# 2. Android SDK
$sdkm = "$Sdk\cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkm)) {
  Step 'Скачиваю инструменты Android (около 150 МБ)'
  $zip = "$Root\cmdline.zip"; $tmp = "$Root\cmdline-tmp"
  Download $SdkUrl $zip
  Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
  Expand-Archive $zip $tmp -Force
  New-Item -ItemType Directory -Force "$Sdk\cmdline-tools" | Out-Null
  Remove-Item "$Sdk\cmdline-tools\latest" -Recurse -Force -ErrorAction SilentlyContinue
  Move-Item "$tmp\cmdline-tools" "$Sdk\cmdline-tools\latest"
  Remove-Item $zip, $tmp -Recurse -Force
}
$env:ANDROID_HOME = $Sdk; $env:ANDROID_SDK_ROOT = $Sdk
if (-not (Test-Path "$Sdk\platforms\android-34") -or -not (Test-Path "$Sdk\build-tools\34.0.0")) {
  Step 'Принимаю лицензии и ставлю Android 14 SDK'
  $yes = (1..40 | ForEach-Object { 'y' }) -join "`n"
  $yes | & $sdkm "--sdk_root=$Sdk" --licenses | Out-Null
  & $sdkm "--sdk_root=$Sdk" 'platforms;android-34' 'build-tools;34.0.0' 'platform-tools'
  if ($LASTEXITCODE -ne 0) { Fail 'не удалось установить компоненты Android.' }
}

# 3. Копия проекта в путь без русских букв
Step 'Готовлю проект'
robocopy $Project $Work /MIR /XD .git build .gradle .idea /XF RadioNet.apk local.properties /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { Fail 'не удалось скопировать проект.' }
Set-Content -Path "$Work\local.properties" -Value ('sdk.dir=' + ($Sdk -replace '\\', '/')) -Encoding ASCII
$env:GRADLE_USER_HOME = "$Root\gradle-home"

# 4. Сборка
Step 'Собираю APK (первый раз Gradle скачивает свои файлы, это дольше)'
Push-Location $Work
& .\gradlew.bat --no-daemon assembleRelease
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { Fail 'сборка не прошла. Пролистайте окно вверх: причина написана перед словами BUILD FAILED.' }

# 5. Готовый файл рядом с проектом
$apk = "$Work\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { Fail 'APK не найден после сборки.' }
$out = Join-Path $Project 'RadioNet.apk'
Copy-Item $apk $out -Force
$size = [math]::Round((Get-Item $out).Length / 1MB, 1)
Write-Host ''
Write-Host "ГОТОВО: $out ($size МБ)" -ForegroundColor Green
Write-Host 'Установка: перекиньте файл на телефон или телевизор и откройте его. В BlueStacks — просто перетащите файл в окно.'
Start-Process explorer.exe "/select,`"$out`""
