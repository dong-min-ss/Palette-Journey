# Palette Journey MVP

차량 여행의 지역 색을 모아 모바일에서 컬러링을 이어가는 공모전 MVP.

현재 구현은 기본 11색·최초 티켓·개인 색 해금·지역 색 중복 수집 방지 규칙이다.
실행: Node 22 이상에서 `npm.cmd run check`.

개발 순서와 단계별 통과 기준은 ROADMAP.md, 현황은 PROGRESS.md, 설계 선택은 DECISIONS.md를 참고한다.
Android 첫 화면은 `android`에 있다. 기본 11색과 개발 상태 안내를 표시하며 계정·차량 API는 아직 연결하지 않았다.
Android 빌드: `cd android` 후 `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`.
SDK 경로는 Git에서 제외되는 android/local.properties에 설정한다.
모바일 웹/백엔드는 아직 구현되지 않았다.
