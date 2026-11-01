# 음성 일기 연동 조사 — 2026-11-01

## 확인한 공식 문서

- [SpeechToText 개요](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/SpeechToText/intro): 차량 Speech Service에 연결해 인식 결과 텍스트를 받는 Android SDK. 문서 기준 v3.3.1, ON_DEVICE/HYBRID/SERVER 모드를 구분하며 서버 모드는 앱 등록이 필요하다.
- [TextToSpeech 개요](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro): 차량 오디오 시스템을 통한 합성·재생 SDK. 문서 기준 v3.3.1이며 모드에 따라 앱 등록이 필요하다.
- [TTS 이벤트](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners): 준비/시작/완료/중지/음량/오류 이벤트가 설명돼 있다. 이 문서만으로 합성 음원 파일·PCM 반환을 확인하지 못했다.
- [SDK 릴리즈 노트](https://document.pleos.ai/en/docs/pleos-only/release-note/sdk-release-note): 현재 공개 문서의 SDK 버전은 에뮬레이터 이미지 v2.1.0과 구분해야 한다. 현 이미지에서의 SDK 호환성은 미검증이다.
- [Gleo Interface 설명이 있는 오디오 앱 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/music-audio-app-developement-guide): 앱 기능/스키마를 등록해 Gleo가 호출하는 연동이다. 이를 임의 웹 챗봇 API나 대화 녹음 권한으로 해석하지 않는다.

## 아직 확인할 부분

- 자유 대화/후속 질문을 위한 실제 SDK/API 접근과 앱 등록, 인증, 이용 조건.
- 차량의 질문 합성 음원과 아이의 답변 음원을 모두 보관할 수 있는 접근 방식, 오디오 동시 사용 및 녹음 허용 범위.
- SDK v3.3.1과 현재 v2.1.0 이미지 간 호환성, 실제 차량 마이크/스피커 라우팅.
- 가족 비공개 녹음 보관과 AI 처리에 필요한 음원/텍스트의 분리, 삭제 범위.

지원하지 않는다고 단정하지 않는다. 위 자료만으로 확인하지 못한 항목이며 실제 개발자 환경 검증이 필요하다. SDK 등록이나 이용 조건 승인은 이 작업에서 수행하지 않았다.

## 지금 구현한 독립 단위

웹에서 보호자가 고정 질문을 읽고 아이가 답하는 녹음 시연이다. 실제 AI·음성 합성·음성 인식·맞춤 후속 질문은 사용하지 않는다. 질문 4개와 시점을 녹음에 연결하며 질문도 마이크에 직접 읽어야 소리로 저장된다.

`/api/session/pages/:id/voice`: GET 메타데이터, PUT 저장, DELETE 삭제. 변경은 음성 전용 revision이 필요하며 충돌은 409다. `/voice/audio`는 동일 가족 세션을 확인한 후 음원 바이트를 반환한다. 음원을 공개 정적 경로로 노출하지 않는다.

최대 120초/2MiB, audio/webm·audio/ogg·audio/mp4의 헤더·크기 검사. 전체 디코딩·트랜스코딩은 없으며 다른 기기에서 해당 형식을 재생하지 못할 수 있다. 실제 마이크 품질과 브라우저별 녹음·재생 검증은 후속이다.

저장 전 음원은 메모리에만 있으며 실패 시 재시도/다운로드할 수 있다. 새로고침 후 미저장 음원 자동 복구는 없다. 녹음 시작 시에만 권한 요청, 일시정지 시 트랙 비활성화, 종료 시 트랙 해제를 적용했다. 보호자 동의 체크는 역할 인증을 대신하지 않는다.

## 2026-11-01 — 차량 TTS 첫 검증 완료
공식 Maven의 TextToSpeech 3.3.1을 앱에 직접 연결하고 ON_DEVICE/Locale.KOREA로 한 문장을 재생했다. 현재 설치된 Pleos v2.1.0/API34 에뮬레이터에서 초기화 성공, TTS_SERVICE granted=true, onReady/onStart/onDone을 확인했다. 이 결과는 해당 조합의 질문 재생 검증이며 STT·자유 대화·다른 SDK 모듈까지 호환된다는 의미는 아니다. 합성 음원 파일/PCM 반환과 웹 녹음 혼합은 여전히 미확인이다. 기기 출력은 SDK 완료 이벤트 기준이며 호스트 스피커의 실제 음질을 청취·측정하지는 않았다.

## 2026-11-01 — STT 구현
[SpeechToText 결과 이벤트](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners)와 [시작/중지 API](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop)를 확인하고 공식 3.3.1 AAR의 실제 메서드 시그니처와 대조했다. 질문 완료 후 request(), 중간/최종 결과, 종료·오류·타임아웃과 정리를 구현했다. 원본 음원 접근·맞춤 후속 질문·웹 녹음 저장은 여전히 별도 과제다.

실마이크 인식은 사용자 직접 시험으로 화면 텍스트 일치를 확인했다. 해당 SDK/이미지 조합은 중간 결과 후 최종 플래그 없이 끝날 수 있어 1초 유예 후 사용자 확인 상태로 전환한다. SDK 확정 결과인 것처럼 처리하지 않는다.

## 2026-11-01 — LLM SDK 종료 확인과 후속 질문 시연
[공식 Playground 변경 안내](https://document.pleos.ai/en/docs/pleos-only/release-note/playground-release-note)는 LLM SDK 종료와 Gleo Interface 사용을 안내한다. [Gleo Interface 개념](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/core-concepts)은 앱이 제공하는 기능과 스키마를 통해 Gleo가 호출하고 콜백을 받는 방식이다. 앱에서 임의의 프롬프트를 보내 자유 대화를 생성하는 API로 해석하지 않는다. [스키마 전달 안내](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema)에는 스키마 검토/승인 절차가 있다. 이번 작업에서는 등록·제출을 하지 않았다.
따라서 현재 구현은 Gleo 생성형 대화가 아닌 준비된 질문 분기다. 확인된 답변의 음식/장소/활동 단어로 중립적인 질문을 고르고, 같은 질문을 반복하지 않으며 최대 네 질문만 진행한다. 방문/음식 섭취를 지어내지 않는다. 음원·대화 기록은 웹/서버에 연결하지 않았다. 실제 글레오 자유 대화 지원과 앱 기능/스키마 범위는 별도 확인이 필요하다.

## 2026-11-01 — 답변 원음과 질문 문장 보관
[공식 PCM 입력 API](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/SpeechToText/sending-audio-data)에 따라 ON_DEVICE 모드의 sendAudio/completeAudioSend를 사용한다. 앱이 단독으로 AudioRecord를 소유하고 같은 PCM을 WAV 기록과 인식에 전달하므로 두 앱 마이크의 동시 수집에 의존하지 않는다.
현재 확인한 TTS 3.3.1 공개 SDK에는 질문 원음 파일을 반환하는 메서드가 확인되지 않았다. 이를 지원 불가로 단정하거나 비공개 API로 우회하지 않고, 원문 질문 문장과 답변 WAV를 보관한다. 재생 시 질문은 같은 Pleos TTS로 재합성하고 답변만 원음을 재생한다. 제품 화면과 manifest.properties의 questionAudio=tts-reconstructed/answerAudio=microphone-original로 출처를 구분한다. 실제 혼합 대화 원음·웹 일기 연결은 미완료다.

검증 결과: 사용자가 새 녹음 방식에서 실제 발화 후 기기 저장·다시 듣기를 직접 수행하여 답변 원음이 들림을 확인했다. 합성 PCM 저장/재개방/MediaPlayer 디코딩/삭제의 기기 계측 시험도 통과했다. 질문 원음과 웹 동기화가 완료됐다는 의미는 아니다.
