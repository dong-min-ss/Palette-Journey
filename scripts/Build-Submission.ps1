param(
    [string]$TeamName = '박동민'
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($TeamName) -or $TeamName.IndexOfAny([IO.Path]::GetInvalidFileNameChars()) -ge 0) {
    throw 'TeamName에는 파일명으로 사용할 수 있는 참가자명 또는 팀명을 입력해주세요.'
}
$repo = Split-Path $PSScriptRoot -Parent
$android = Join-Path $repo 'android'
$signing = Join-Path $android '.signing'
$properties = Join-Path $signing 'release.properties'
$keystore = Join-Path $signing 'submission.p12'
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/keytool.exe' } else { 'keytool' }
New-Item -ItemType Directory -Path $signing -Force | Out-Null
if (!(Test-Path -LiteralPath $properties)) {
    if (Test-Path -LiteralPath $keystore) { throw '기존 서명 키의 release.properties를 복구해주세요. 키를 덮어쓰지 않습니다.' }
    $random = [byte[]]::new(32)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($random) } finally { $generator.Dispose() }
    $password = [Convert]::ToBase64String($random)
    $env:PALETTE_SIGNING_PASSWORD = $password
    try {
        & $keytool -genkeypair -noprompt -keystore $keystore -storetype PKCS12 -alias submission `
            -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Palette Journey Submission' `
            -storepass:env PALETTE_SIGNING_PASSWORD -keypass:env PALETTE_SIGNING_PASSWORD
        if ($LASTEXITCODE) { throw '제출용 서명 키 생성 실패' }
        $content = "storeFile=.signing/submission.p12`nstorePassword=$password`nkeyAlias=submission`nkeyPassword=$password`n"
        [IO.File]::WriteAllText($properties, $content, [Text.UTF8Encoding]::new($false))
    } finally { Remove-Item Env:PALETTE_SIGNING_PASSWORD -ErrorAction SilentlyContinue }
}
Push-Location $android
try {
    & ./gradlew.bat :app:assembleRelease :companion:assembleRelease -PpaletteSubmissionDemo=true --console=plain
    if ($LASTEXITCODE) { throw '제출용 release 빌드 실패' }
} finally { Pop-Location }
$outputs = @(
    @{ Source = 'app/build/outputs/apk/release/app-release.apk'; Name = "${TeamName}_PaletteJourney_release.apk" },
    @{ Source = 'companion/build/outputs/apk/release/companion-release.apk'; Name = "${TeamName}_PaletteJourneyCompanion_release.apk" }
)
foreach ($output in $outputs) {
    Copy-Item -LiteralPath (Join-Path $android $output.Source) -Destination (Join-Path $repo "apk/$($output.Name)")
}
$checksums = foreach ($file in (Get-ChildItem -LiteralPath (Join-Path $repo 'apk') -Filter '*.apk' | Sort-Object Name)) {
    "$((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($file.Name)"
}
[IO.File]::WriteAllText((Join-Path $repo 'apk/SHA256SUMS'), (($checksums -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
Write-Host '서명된 제출용 release APK와 SHA256SUMS를 apk/에 생성했습니다.'
Write-Host 'android/.signing/의 키와 비밀번호는 Git에서 제외됩니다. 업데이트를 위해 비공개로 백업해주세요.'
