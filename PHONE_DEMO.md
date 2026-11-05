# 안드로이드 폰 보조 앱 시연

차량용 `:app`과 별도 설치되는 `:companion` APK다. 기존 웹 UI와 서버를 재사용하는 Android WebView 보조 앱이며, 그림과 마이크 녹음을 같은 폰 화면에서 완성한다. 차량 녹음 파일을 골라 옮기는 과정은 기본 시나리오에서 제외한다. 실제 생성형 글레오 자유 대화는 후속 기능이다.

## 실행

필수 환경은 Windows, Node 24+, Android SDK/Emulator, API 37 Google Play x86_64 시스템 이미지와 기존 Gradle 빌드 환경이다. 기본 SDK 경로가 다르면 `-Sdk`를 지정한다.

저장소 루트 PowerShell에서 실행한다.

```powershell
./scripts/Start-PhoneDemo.ps1
# 이미 APK를 빌드했다면
./scripts/Start-PhoneDemo.ps1 -SkipBuild
```

스크립트는 보조 앱을 빌드하고, 서버가 없으면 기존 `.env`로 서버를 시작한다. `%TEMP%/PaletteJourneyPhone`에 별도 세로형 AVD를 생성하고 `emulator-5556`으로 실행한다. 기존 차량 AVD/가족 기록을 초기화하지 않는다. 초기 부팅은 수 분 걸릴 수 있다. Windows 그래픽 호환성을 위해 소프트웨어 그래픽을 사용한다.

APK: `android/companion/build/outputs/apk/debug/companion-debug.apk`.
차량 APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
각각 별도 패키지이며 Android 개발용 자동 서명 APK다.

## 같은 가족에서 작성

1. 차량 **가족 연결 → 휴대폰 연결 QR 발급 · 5분**. QR에 포함된 24자리 코드를 폰 보조 앱에 입력한다. 기존 웹의 **차량 연결 → 다른 기기 연결 코드 발급**도 같은 가족 초대를 발급한다. 폰 에뮬레이터에서 실제 카메라 QR 스캔은 제공하지 않는다.
2. 폰에서 그림·스티커를 작성한다. **이 그림에 목소리 남기기**의 보호자 동의를 선택하고 **목소리 녹음하기**를 누른다. 첫 사용에는 Android 마이크 권한을 허용한다.
3. 질문을 읽거나 자유롭게 이야기한다. **녹음 마치기 → 미리 듣기 → 이 그림에 목소리 저장**. 녹음 시작 전에 그림 저장을 완료하며, 녹음은 현재 페이지에 바로 연결한다. 별도 페이지 선택·차량 업로드가 필요 없다.
4. 다시 앱을 열어 같은 그림의 **대화 녹음 재생**을 확인한다. 차량 **여행책 감상 → 해당 그림 → 대화 듣기**로 같은 기록을 감상한다.

실제 목소리를 시연하려면 폰 에뮬레이터 **… → Microphone → Virtual microphone uses host audio input**을 켠다. PC의 입력 장치가 정상인지 확인한다. 자동 시험은 호스트 마이크를 끄고 수행하므로 사람 목소리 음질 검증을 대신하지 않는다.

## 서버 연결 방식과 한계

시연 앱은 `http://localhost:3000`만 열고 마이크 접근을 허용한다. `adb reverse tcp:3000 tcp:3000`이 폰의 루프백을 PC 서버에 연결한다. localhost의 보안 컨텍스트와 Android의 명시적 마이크 권한을 사용하며 인증서 설치나 보안 경고 우회는 필요 없다. 임의 HTTP 사이트·카메라 권한·JavaScript 네이티브 브리지는 허용하지 않는다. [Android PermissionRequest](https://developer.android.com/reference/android/webkit/PermissionRequest).

PC 서버가 켜져 있어야 작성·동기화할 수 있다. 인터넷 배포/오프라인 독립 앱은 이번 범위가 아니다. 재부팅 후 연결이 끊기면 시작 스크립트를 다시 실행한다. 실기기 Android에서도 USB 디버깅과 adb reverse로 시험할 수 있지만, 이번 검증 대상은 폰 에뮬레이터다. LAN HTTP로 여는 기존 웹은 여전히 휴대폰 마이크가 제한된다.

최대 녹음 2분/2MiB, 페이지별 1개. 녹음 저장 실패 시 같은 화면에서 재시도할 수 있다. 저장 전 녹음은 RAM에 있으므로 앱 종료·새로고침 전에 반드시 저장한다. 앱에서는 별도 파일 내려받기 기능을 숨긴다. 앱을 벗어나면 녹음은 일시정지하며 자동 재개하지 않는다.

## 개발자 기기 검사

사용자 데이터와 분리된 `node scripts/phone-test-server.mjs`를 실행한다. 테스트 서버는 127.0.0.1:3001의 메모리 DB이고 초대 코드는 Git에서 제외한 `evidence/phone-fixture.json`에 기록한다. 5분 내 실행하며 재시험 때 서버를 재시작해 새 코드를 발급한다.

```powershell
cd android
./gradlew.bat :companion:assembleDebug :companion:assembleDebugAndroidTest
cd ..
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
& $adb -s emulator-5556 install -r android/companion/build/outputs/apk/debug/companion-debug.apk
& $adb -s emulator-5556 install -r android/companion/build/outputs/apk/androidTest/debug/companion-debug-androidTest.apk
& $adb -s emulator-5556 emu avd hostmicoff
& $adb -s emulator-5556 reverse tcp:3000 tcp:3001
$fixture = Get-Content evidence/phone-fixture.json -Raw | ConvertFrom-Json
& $adb -s emulator-5556 shell am instrument -w -e code $fixture.code com.palettejourney.companion.test/androidx.test.runner.AndroidJUnitRunner
# 시험 후 본 시연 서버 연결 복구
& $adb -s emulator-5556 shell pm clear com.palettejourney.companion
& $adb -s emulator-5556 reverse tcp:3000 tcp:3000
```

`pm clear`는 시험 전용 폰 앱 연결/임시 기록만 지운다. 사용자 가족으로 연결한 뒤에는 실행하지 않는다. 기기 검사는 실제 WebView 터치 획·MediaRecorder 녹음·일시정지·서버 저장·재접속 재생과 여행책 버튼 간격을 검증한다.
