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

실차·릴리스 빌드 시험은 잠겨 있다. 실제 차량 상태가 연결되기 전에는 에뮬레이터 진단만 제공한다. 맞춤 후속 질문, TTS 음원 파일 추출, 웹 음성 일기 저장은 아직 연결하지 않았다.

## 질문 후 답변 인식
SpeechToText 3.3.1과 STT_SERVICE를 추가했다. ON_DEVICE/Locale.KOREA 사용. 앱 아래 보호자 마이크 허용 체크 후 **질문 듣고 답하기 / 다시 말하기**를 누른다. STT 서비스 준비 → TTS 질문 재생 → 완료 콜백 → request() 순서로 마이크를 연다. 준비는 20초, 답변은 30초 제한이며 오류/무응답 시 자동 반복하지 않는다. **답변 인식 취소** 또는 화면 이탈 시 stop/removeListener/release한다.
확정 전 텍스트는 중간 결과라고 표시하며 최종 결과만 완료로 표시한다. 확인한 답변 원음과 텍스트는 명시적 저장으로 앱 내부에 보관할 수 있다. 서버·웹/앱 자체 로그에는 전송·기록하지 않는다. 미저장 원음은 화면 이탈 시 지운다. 이 단계는 한 번의 질문·답변 시험으로 맞춤 대화·웹 저장은 미연결이다. 실기기 음성 인식 정확도는 별도 검증이 필요하다.
- [STT 설치](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk)
- [STT 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions)
- [인식 결과 이벤트](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners)

2026-11-01 실마이크 시험: 사용자 허용으로 Host Microphone Access를 켜고 사용자가 직접 발화해 화면 텍스트 일치를 확인했다. 해당 엔진에서 최종 completed 플래그 없이 종료하는 경우가 있어 1초 대기 후 마지막 중간 텍스트를 보존한다. **들은 내용이 맞아요**로 명시 확인하거나 **다시 말하기**로 재시도한다. SDK의 최종 결과 수신과 사용자 확인은 구분한다. 상태 검증은 5개, 기존 테스트 포함 총6개 통과했다.

## 후속 질문 시연
**대화 시연 시작**으로 첫 질문을 듣고 답한다. **들은 내용이 맞아요 / 다음 질문**을 누르면 답변을 화면 기록에 넣고 준비된 후속 질문으로 이어간다. 인식 결과가 SDK에서 확정된 경우에도 보호자 확인 전에는 다음 질문을 고르지 않는다. 질문마다 최대30초, 최대4차례. 음식/장소/활동/기분/기억 등의 사전 문구를 고르는 규칙 시연이며 실제 생성형 AI가 아니다.
**질문 건너뛰기**는 현재 인식을 취소하고 다음 질문에서 대기한다. **대화 종료**는 마이크와 질문을 중지한다. 다시 답하기는 현재 질문을 유지하며 확인 전 결과만 바꾼다. 새 대화 시작이나 화면 이탈은 화면의 대화 기록을 지운다. 답변 원음의 기기 보관을 지원하며 웹 동기화는 미연결이다.

## 답변 녹음과 기기 보관
1. 안내 확인 후 대화 시작. Android 마이크 권한을 허용한다. 질문 재생 중에는 녹음하지 않는다.
2. 질문 종료 후 한 번 열린 AudioRecord 입력을 WAV 파일과 SDK sendAudio에 함께 보낸다. SDK의 request()와 앱 마이크를 동시에 열지 않는다.
3. 답변 확인을 누른 원음만 보관 후보에 넣는다. 질문 건너뛰기/재답변/취소의 미확인 원음은 삭제한다. 대화 종료 후 저장 버튼을 누른다.
4. 저장한 이야기 듣기·삭제 화면에서 질문 문장 TTS → 답변 원음 순서로 다시 듣는다. 목록 선택/재생 중지/삭제를 제공한다.
기기 files/voice-archives 아래 질문·확인한 텍스트·WAV가 앱 전용으로 저장된다. 최대20개 이야기, 이야기당 최대4개 답변, 답변당 최대30초(약960KB)다. 저장 도중 실패한 묶음은 목록에 노출하지 않는다. cache/voice-drafts 임시 원음은 화면 이탈 또는 다음 실행 때 지운다. 앱 제거/데이터 삭제 시 저장 원음도 사라지며 별도 내보내기/서버 복구는 없다. allowBackup=false, 재생 화면 exported=false.
질문은 원본 음원 파일이 아니라 TTS 재합성이다. 웹 일기 페이지/가족 세션과의 연결 및 질문 원음 확보는 후속이다. 이번 음성 저장은 그림일기 웹의 음성 저장과 별개다.

검증: Android 단위16개, 기기 저장/MediaPlayer 디코딩 계측1개, 웹·서버30개 통과. 사용자 허용 하에 실제 발화→녹음 저장→다시 듣기를 수행했고 답변 원음 재생을 확인받았다. 실차 녹음과 기기 간 호환성은 별도 검증이 필요하다.
