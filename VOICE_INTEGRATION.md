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
