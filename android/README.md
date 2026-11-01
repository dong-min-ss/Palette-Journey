# Android 차량 앱 개발

기본 팔레트와 에뮬레이터 전용 질문 음성 재생 시험을 제공한다. 현재 계정/웹/차량 상태는 연결 전이다.

## 빌드와 실행

1. `local.properties`에 로컬 SDK 경로 설정(Git 제외).
2. 이 폴더에서 `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` 실행.
3. Pleos v2.1.0/API34 에뮬레이터를 실행하고 app-debug.apk 설치/앱 실행.
4. 화면의 **질문 한 문장 듣기**를 누르면 “오늘 어떤 일이 있었어?”를 재생한다.

공식 Maven의 `ai.pleos.playground:TextToSpeech:3.3.1`, `pleos.car.permission.TTS_SERVICE`, `Mode.ON_DEVICE`, `Locale.KOREA`를 사용한다. 이 모드는 앱 등록 없이 온디바이스 질문 재생을 검증하기 위한 선택이다. 서버 모드 등록은 수행하지 않았다.

- [SDK 추가](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk)
- [권한 선언](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions)
- [초기화/해제](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release)
- [한국어 설정](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/set-locale)

## 검증과 한계

2026-11-01: 최종 APK 빌드/단위 테스트/설치 성공. 현재 Pleos v2.1.0/API34에서 권한 허용, SDK 초기화 성공, 질문 버튼 클릭 후 재생 시작/완료 콜백을 확인했다. `adb logcat -s PaletteTts:I AndroidRuntime:E`로 해당 결과를 확인할 수 있다. 호스트 출력 음질은 청취·측정하지 않았다.

자동 instrumentation 환경에서는 초기화 응답 지연이 있어 자동 UI 통과를 주장하지 않는다. 일반 앱 실행에서 Computer Use로 버튼을 조작하고 상태/로그를 확인했다.

실차·릴리스 빌드 시험은 잠겨 있다. 실제 차량 상태가 연결되기 전에는 에뮬레이터 진단만 제공한다. 마이크 음성 인식, 맞춤 후속 질문, TTS 음원 파일 추출, 웹 음성 일기 저장은 아직 연결하지 않았다.
