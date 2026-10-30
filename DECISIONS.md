# 결정 기록

## 2026-10-30
- 기존 Android 원본에서 소스/리소스/Wrapper/빌드 설정만 android로 선별 복사했다. 기존 .idea/.gradle/build/local.properties는 복사하지 않았다.
- 첫 화면은 기본 11색과 연결 전 상태만 표시한다. 차량 상태 미확인으로 연결·창작 조작을 제공하지 않는다.
- 사용자 지정 한글 경로를 유지하기 위해 android.overridePathCheck=true를 적용하고 실제 빌드/실행으로 검증한다.
- Gradle9.6에서 Windows 한글 경로 테스트 클래스 로딩 실패를 확인하여 공식 수정이 포함된 9.7.0으로 변경했다. 배포본 공식 SHA256을 고정했다. https://github.com/gradle/gradle/issues/30304
- Git 커밋 작성자는 연결된 GitHub 계정 dong-min-ss 및 계정ID188013628을 확인해 저장소 로컬 설정에 GitHub noreply 주소를 사용한다.
- 기존 문서·스크립트·qa 폴더를 수정하지 않고 하위 palette-journey에 개발한다. 다른 기존 작업 폴더는 수정하지 않는다.
- 상위 루트는 Git 저장소가 아니다. 사용자 URL 답변에 따라 palette-journey 안에 Git main을 초기화하고 origin을 지정했다. 원격 접근이 해결되기 전 별도 이력을 만들거나 푸시하지 않는다.
- Node 내장 테스트로 외부 설치 없이 도메인 규칙부터 검증한다. 현재 core는 메모리 내 순수 함수이며 서버 영속성·인증·동시성 구현을 대신하지 않는다.
- 기본 11색의 HEX는 시연용 초기값이며 기획서 색 이름을 따른다. 최초 티켓은 계정 생성 시 1장, 지역 색 수집은 무료다.
- Android minSdk 28 이상/API34 에뮬레이터를 기준으로 한다. Pleos API 의존성 버전은 에뮬레이터 버전과 구분하여 실제 SDK 확인 후 고정한다.
- SDK/ADB가 확인되기 전 Gradle 빌드나 차량 API 작동을 주장하지 않는다.
- 앱 실행 중 자율 개발은 이 채팅의 작업 루프로 진행한다. 상시 백그라운드 실행·알림 자동화는 아직 구성되지 않았다.

참고: https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
