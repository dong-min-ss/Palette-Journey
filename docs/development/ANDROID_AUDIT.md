# Android 프로젝트 읽기 전용 조사

조사일: 2026-10-30
원본: C:\Users\qkreh\AndroidStudioProjects\PaletteJourney

## 결과

- Android Studio가 생성한 단일 app 모듈의 초기 골격이다.
- main 소스에 Kotlin/Java Activity가 없고 Manifest의 application은 자식 요소 없이 닫힌다. LAUNCHER Activity가 없어 실행할 앱 화면이 없다.
- Gradle Wrapper 스크립트·JAR가 모두 있다. 전역 Gradle 설치 없이 Wrapper를 사용할 수 있다.
- 사용자 Gradle 캐시에 Gradle 9.6.0 배포본의 gradle.bat와 라이브러리가 현재 존재한다. 최초 조사 때 발견하지 못했던 실행 파일이 이번 조사에서 확인됐다.
- AGP 9.5.0, Gradle 9.6.0, 배포 ZIP SHA256이 설정돼 있다. 버전 호환성은 실제 빌드에서 추가 확인해야 한다.
- namespace/applicationId: com.palettejourney.
- compileSdk 37, targetSdk 37, minSdk 34. API34 에뮬레이터 이미지와 compileSdk는 별개다. compileSdk34 부재만으로 빌드 차단이라고 판단했던 초기 기록을 정정한다.
- 기본 의존성은 AppCompat/Core KTX/Material/JUnit/Espresso이며 Pleos SDK 의존성과 차량·위치 연동 코드는 없다.
- 저장소는 google/mavenCentral 및 플러그인 포털을 사용한다. Pleos SDK 저장소 설정은 없다.
- 런처 아이콘·테마·백업 규칙은 있으나 컬러링/QR/세션/네트워크 기능은 없다.
- 기존 테스트는 2+2=4 및 앱 패키지명 확인용 템플릿이다. MVP 기능 검증은 아직 없다. 원본에 .git 폴더는 없다.

## 재사용할 부분

Gradle Wrapper, 빌드 설정, 버전 카탈로그, 앱 식별자, 기본 리소스를 시작점으로 재사용할 수 있다. 기존 앱 기능을 병합할 필요는 없는 초기 상태다.

## 후속 처리

1. 원본을 보존하며 현재 저장소의 android 아래에 소스·리소스·Wrapper·설정만 선별 복사한다. .idea/.gradle/build/local.properties와 인증·키 파일은 가져오지 않는다.
2. SDK 경로는 복사본의 Git 제외 local.properties로 설정한다.
3. Wrapper 배포본과 플러그인/SDK의 실제 호환성을 확인한다. 원본 버전이 맞지 않으면 복사본에서 조정하고 기록한다.
4. 최소 Activity와 런처 진입점을 만들고 assembleDebug/단위 테스트/에뮬레이터 설치·실행을 검증한다.

이번 조사는 원본 파일 읽기만 수행했다. 원본에서 빌드를 실행하지 않았으며 복사·수정·이동하지 않았다. 빌드 성공, APK 설치·화면 실행, 차량 API 작동은 아직 검증하지 않았다.
