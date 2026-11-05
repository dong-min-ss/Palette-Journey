param(
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$Serial = 'emulator-5556',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
$emulator = Join-Path $Sdk 'emulator/emulator.exe'
$image = Join-Path $Sdk 'system-images/android-37.0/google_apis_playstore/x86_64'
if (!(Test-Path -LiteralPath $image)) { throw 'Android Studio SDK Manager에서 API 37 Google Play x86_64 시스템 이미지를 설치해주세요.' }
if (!(Test-Path -LiteralPath $adb)) { throw 'Android SDK 경로를 -Sdk 인수로 지정해주세요.' }
if (!$SkipBuild) {
    Push-Location (Join-Path $repo 'android')
    try { & ./gradlew.bat :companion:assembleDebug --console=plain; if ($LASTEXITCODE) { throw 'APK 빌드 실패' } }
    finally { Pop-Location }
}
$avdHome = Join-Path $env:TEMP 'PaletteJourneyPhone'
$avd = Join-Path $avdHome 'Palette_Phone.avd'
New-Item -ItemType Directory -Path $avd -Force | Out-Null
$env:ANDROID_AVD_HOME = $avdHome
$ini = "avd.ini.encoding=UTF-8`npath=$avd`ntarget=android-37`n"
[IO.File]::WriteAllText((Join-Path $avdHome 'Palette_Phone.ini'),$ini)
$config = @"
AvdId=Palette_Phone
avd.ini.displayname=Palette Journey Phone
avd.ini.encoding=UTF-8
abi.type=x86_64
tag.id=google_apis_playstore
tag.display=Google Play
target=android-37
image.sysdir.1=$image/
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.initialOrientation=portrait
hw.keyboard=yes
hw.audioInput=yes
hw.battery=yes
hw.mainKeys=no
hw.camera.back=none
hw.camera.front=none
hw.gps=yes
hw.accelerometer=yes
disk.dataPartition.size=4G
showDeviceFrame=no
fastboot.forceColdBoot=yes
PlayStore.enabled=true
"@
[IO.File]::WriteAllText((Join-Path $avd 'config.ini'),$config)
try { Invoke-WebRequest -UseBasicParsing 'http://localhost:3000/' -TimeoutSec 3 | Out-Null }
catch {
    Start-Process node -ArgumentList '--env-file-if-exists=.env','backend/server.mjs' -WorkingDirectory $repo -WindowStyle Hidden
    Start-Sleep -Seconds 2
    Invoke-WebRequest -UseBasicParsing 'http://localhost:3000/' -TimeoutSec 5 | Out-Null
}
$devices = & $adb devices
if (!($devices -match "^$([regex]::Escape($Serial))\s+")) {
    $port = $Serial -replace '^emulator-',''
    if ($port -notmatch '^\d+$') { throw 'Serial은 emulator-5556 형식이어야 합니다.' }
    Start-Process $emulator -ArgumentList '-avd','Palette_Phone','-port',$port,'-no-snapshot','-gpu','swiftshader','-feature','-Vulkan','-no-boot-anim' -WorkingDirectory $repo -RedirectStandardOutput (Join-Path $avdHome 'phone-out.log') -RedirectStandardError (Join-Path $avdHome 'phone-error.log')
}
$deadline = (Get-Date).AddMinutes(4)
do {
    Start-Sleep -Seconds 2
    $ErrorActionPreference = "Continue"
    $boot = [string](& $adb -s $Serial shell getprop sys.boot_completed 2>$null)
    $ErrorActionPreference = "Stop"
    if ((Get-Date) -gt $deadline) { throw '폰 에뮬레이터 부팅 시간 초과. Android Studio에서 상태를 확인해주세요.' }
} while ("$boot" -notmatch "^1")
& $adb -s $Serial reverse tcp:3000 tcp:3000
if ($LASTEXITCODE) { throw '서버 연결 실패' }
& $adb -s $Serial install -r (Join-Path $repo 'android/companion/build/outputs/apk/debug/companion-debug.apk')
if ($LASTEXITCODE) { throw '보조 앱 설치 실패' }
& $adb -s $Serial shell am start -n com.palettejourney.companion/.CompanionActivity
Write-Host '폰 보조 앱 실행 완료. 차량 QR의 24자리 연결 코드를 입력하세요.'
Write-Host '실제 목소리 시연: 에뮬레이터 … > Microphone > Virtual microphone uses host audio input을 켜주세요.'
