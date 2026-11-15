# 콘솔로 도시 방문·해금 시연

차량 첫 화면의 **개발 도구 → 개발자 주행 시연**을 누른다. 가족 연결의 **개발용 시연** 안에서도 들어갈 수 있다. debug 또는 시연 옵션을 명시한 제출용 release의 Pleos 에뮬레이터와 `PALETTE_MOCK_LOCATION=1` 개발 서버에서만 동작한다. 제출 APK 설치는 [MVP 시연 가이드](MVP_FLOW.md)를 따른다.

1. 기존 시연 가족이면 그대로 사용한다. 일반 가족이거나 연결이 없으면 **새 시연 가족**을 누른다. 새 가족 생성은 기존 휴대폰 가족과 별개이므로 필요할 때만 사용한다.
2. 이미 획득한 보상을 다시 시험하려면 **지역 방문 초기화 → 초기화**를 누른다. 같은 시연 가족의 5도시 방문 보상을 모두 다시 잠근다. 기본·기존 개인 색, 그림·녹음, 가족 연결은 유지한다. 기존 그림 속 지역 콘텐츠는 보존되지만 새 편집에 사용하려면 다시 획득해야 한다.
3. **부산**을 누른다. 오른쪽 부산 컬렉션이 색0/3·스티커0/2인지 확인한다. 도시 선택 즉시 시연이 시작된다.
4. Virtual Console `http://127.0.0.1:8088/`에서 **D → ACCEL을 길게 누르기**. 차량 화면 속도·기어와 **모의 주행 0 / 300m**가 변한다. ACCEL은 클릭 한 번이 아니라 누르고 유지한다. 콘솔 주소가 응답하지 않으면 `adb -s emulator-5554 forward tcp:8088 tcp:8088`로 개발 연결을 복원한다.
5. 300m 도착 시 **P**로 바꾸고 속도0에서5초 기다린다. **부산 수집 완료 · 색3개/스티커2개**, 오른쪽 색3/3·스티커2/2 및 **보유 · 모의 보상**을 확인한다. 아래 스티커는 오른쪽 영역을 스크롤해서 볼 수 있다.
6. **제주** 등 다른 도시를 누르면 새 경로가0m부터 시작된다. D·ACCEL→300m→P·5초를 반복하면 선택 도시만 추가 해금된다. 이미 획득한 도시의 재선택·재접속은 중복 지급하지 않는다.
7. **지역 방문 초기화 → 초기화** 후 모든 도시가 다시0/3·0/2가 된다. 도시는 다시 선택해야 하며 이전 주행 요청으로 즉시 재해금되지 않는다.

이번 제출 APK 촬영 환경에서는 D 주행 중 Pleos 시스템이 **주행 중에는 사용할 수 없습니다** 화면을 표시했다. 화면 잠금을 우회하지 않고 콘솔에서 모의 주행을 진행한 뒤 P·속도0으로 복귀했다. 정차 후 앱 화면에서 300m 도착과 5초 정차에 따른 보상 지급을 확인했다. 주행 중 창작 화면을 사용할 수 있다고 해석하지 않는다.

휴대폰은 **휴대폰 연결**에서 동일 시연 가족의 QR로 연결한다. 기존 연결을 유지한 채 초기화하면 QR 재발급 없이 웹 사전에도 보유/잠금이 다음 자동 갱신(약5초)에 반영된다. 새 시연 가족을 만들었다면 새 QR 연결이 필요하다. QR 화면을 다녀오면 시연은 일시정지되므로 **시연 이어가기**를 누른다. 같은 Wi-Fi/핫스팟 및 현재 PC 접속 주소를 사용한다.

이 경로는 콘솔의 실제 SDK 속도·기어를 읽어서 만든 **개발용 가상 경로**다. 표시하는 모의 위도·경도는 에뮬레이터 GPS를 수정하지 않는다. 실제 도로거리나 부산으로 향하는 방향을 판정하지 않는다. 기존 GPS 진입·120초 체류 경로는 별도로 유지한다. 두 경로는 동일한 가족/도시/simulation 보상 영수증을 사용하므로 서로 중복 지급하지 않는다. 일반 가족과 개발용 스티커 접근은 초기화 대상이 아니다.

## Windows에서 에뮬레이터가 종료되는 경우

이 PC에서는 Windows 이벤트 로그에 `qemu-system-x86_64-headless.exe`의 네이티브 충돌(`0xc0000602`, `0xc0000005`)이 기록됐다. 앱의 Java 예외로 종료된 기록과 구분한다. SwiftShader 조건에서는 호스트 오디오를 꺼도 `0xc0000005`가 재현됐다. 같은 제출 APK를 호스트 GPU로 바꿔 실행한 조건에서 전체 흐름을 2회 연속 통과했다. 아래는 해당 PC에서 검증한 실행 조건이며, 특정 GPU 드라이버 내부 결함까지 확정한 것은 아니다.

별도 PowerShell 터미널에서 아래처럼 실행한다. SDK 경로가 다르면 `$sdk`를 변경한다. 캐시는 임시 폴더를 사용하며 기존 AVD 설정을 수정하지 않는다.

```powershell
$sdk = "$env:USERPROFILE/AppData/Local/Android/Sdk"
$driveCache = Join-Path $env:TEMP 'palette-drive-verification-cache.img'
& "$sdk/emulator/emulator.exe" -avd Pleos_Connect_v2.1.0 -port 5554 -read-only -no-snapshot -no-window -no-audio -memory 3072 -cores 4 -cache $driveCache -gpu host -feature -Vulkan -no-boot-anim
```

`-gpu host`는 이 PC의 Intel Arc GPU를 사용한다. 검증한 환경은 Android Emulator 37.2.13, Pleos Connect v2.1.0(API34), Intel 드라이버 32.0.101.8991이다. [Android 그래픽 가속 안내](https://developer.android.com/studio/run/emulator-acceleration). `-no-audio`는 주행 검증과 음성 검증을 분리하기 위한 설정이다. 이 실행으로 음성 청취 품질을 검증하지 않는다. [Android 공식 옵션 안내](https://developer.android.com/studio/run/emulator-commandline).

## release 연속 주행 자동 검증

에뮬레이터가 부팅된 뒤 실행한다. 별도로 켜둔 시연 서버가 있다면 먼저 정상 종료해 PC의 3000 포트를 비운다. 검증 도구는 메모리 전용 서버를 직접 시작하고 종료하며, `backend/data/`를 읽거나 변경하지 않는다. 포트가 사용 중이면 기존 서버를 강제로 종료하지 않고 검증을 중단한다.

```powershell
# 저장소 루트
$sdk = "$env:USERPROFILE/AppData/Local/Android/Sdk"
./scripts/Build-Submission.ps1 -TeamName '박동민'
Push-Location android
./gradlew.bat :app:assembleReleaseAndroidTest -PpaletteSubmissionDemo=true -PpaletteTestBuildType=release --console=plain
Pop-Location
$adb = "$sdk/platform-tools/adb.exe"
& $adb -s emulator-5554 install -r 'apk/박동민_PaletteJourney_release.apk'
& $adb -s emulator-5554 install -r android/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
& $adb -s emulator-5554 shell pm grant --user current com.palettejourney.submission android.car.permission.CAR_SPEED
npm.cmd run verify:drive -- --serial emulator-5554 --sdk $sdk --runs 2
```

검증은 Virtual Console의 D·속도 입력을 실제 차량 SDK로 수신한다. 부산·제주 각 300m 도착, 정차 전 보상 미지급, P·속도 0의 정차 후 도시별 색 3개·스티커 2개, 같은 가족의 별도 참여자 조회, 지역 방문 초기화 후 5도시 색·스티커 재잠금과 보상 이력 삭제, 기본 11색·기존 합성 일기 보존을 확인한다. 실제 폰 UI의 자동 갱신과 실차 방문 인증은 이 검사 범위에 포함하지 않는다.

성공하면 `PASS: 2 complete consecutive drive/reward/reset runs`가 출력된다. 실행별 로그·단계·소요 시간은 Git에서 제외된 `evidence/drive-verification/`에 남는다. 종료 시 콘솔은 P·속도 0으로 복구한다.
