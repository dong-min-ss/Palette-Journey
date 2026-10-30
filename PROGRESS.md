# 개발 현황

## 2026-10-30 — Android 첫 화면 빌드·실행 완료

- 기존 원본에서 선별 복사한 android 프로젝트에 MainActivity와 LAUNCHER를 구현했다. 기본 11색, 첫 가입 티켓 안내, 차량/계정 미연결·조작 잠김 상태를 표시한다.
- Gradle9.6에서는 APK 빌드가 성공했으나 테스트가 ClassNotFoundException으로 실패했다. 한글 경로 argfile 인코딩 버그 수정이 포함된 Gradle9.7.0으로 변경하고 공식 SHA256을 고정했다.
- 최종 `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest --console=plain`: BUILD SUCCESSFUL (40 tasks). Android 테스트는 기존 템플릿 테스트로 실행 환경 확인 범위다.
- `npm.cmd run check`: 팔레트 규칙 테스트3개 통과.
- ADB install -r 성공, am start -W 결과 Status:ok / MainActivity COLD 실행 성공. 화면 캡처로 11색 표시와 안내 문구 확인.
- 앱 PID6562의 crash 버퍼 출력 없음, topResumedActivity가 com.palettejourney/.MainActivity임을 확인했다. 시스템 서비스의 과거 크래시와 앱 크래시는 구분했다.
- 스크린샷은 Git 제외 evidence/android-first-screen.png에 보관한다.
- Android Studio에서 상위 폴더에 발견된 Gradle 설정 파일의 연결을 진행했다. IDE 전체 동기화/Run 구성 완료는 별도 확인이 필요하다. CLI 빌드·ADB 실행은 완료됐다.
- GitHub 계정으로 확인된 작성자와 noreply 주소를 저장소 로컬 Git에 설정했다. 커밋·푸시 결과는 다음 기록 참조.

## 2026-10-30 — 1단계 기존 Android 프로젝트 조사 완료

- 상세 결과: ANDROID_AUDIT.md.
- 기존 프로젝트는 단일 app 모듈 초기 골격이며 Wrapper가 존재한다. Activity/LAUNCHER 및 Pleos SDK 연동은 없다.
- AGP 9.5.0 / Gradle 9.6.0 / compileSdk·targetSdk37 / minSdk34 확인. 실제 버전 호환성·빌드는 미검증.
- 초기 조사 정정: 전역 Gradle 없이 기존 Wrapper를 재사용할 수 있다. compileSdk37을 사용하므로 SDK34 플랫폼 부재 자체는 빌드 차단 근거가 아니다.
- 현재 사용자 캐시에서 Gradle9.6.0의 gradle.bat와 라이브러리 존재도 확인했다.
- 원본을 수정하거나 빌드를 실행하지 않았다. 후속 단계는 선별 복사본에서 빌드와 최소 실행 화면 구성이다.
- 로컬 Git 인증은 사용자 토큰 등록 후 ls-remote 종료 코드0으로 확인됐다. 기존 404/인증 차단 기록은 해결된 과거 상태다.
- 커밋 작성자 이름·이메일은 아직 설정되지 않아 커밋·푸시는 미실행.

## 2026-10-30 — 환경 조사와 첫 기능

### 완료
- 현재 작업 경로: C:\Projects\ChatGPT\현대 공모전.
- 루트에 기획 DOCX 2개, 문서 스크립트 2개, qa 폴더 3개가 있다. 기존 파일은 수정하지 않았다.
- 루트는 Git 저장소가 아니며 remote가 없다. 별도 palette-journey 작업 폴더를 만들었다.
- Node v24.21.0, Java Temurin 21.0.12, Git 실행 가능.
- Android Studio 설정에서 SDK 경로 확인: C:\Users\qkreh\AppData\Local\Android\Sdk.
- ADB: 샌드박스 내 서버 시작 실패. 외부 실행으로 emulator-5554 / Connect_on_x86_64_emulator 연결 성공.
- ADB getprop ro.build.version.sdk = 34, sys.boot_completed = 1.
- 설치 이미지 package.xml: Pleos Connect v2.1.0 APIs. Emulator 실행 도구 자체는 37.2.13.0이며 이미지 버전과 다르다.
- AVD Automotive_1408p_landscape: forceColdBoot=yes, hw.ramSize=2048. 공식 권장 4GB와 차이가 있으나 설정은 변경하지 않았다.
- 기본 11색/최초 티켓 1장/개인 색 해금/지역 색 무료 수집과 중복 방지 구현.
- `npm.cmd run check`: 구문 검사 및 테스트 3개 성공, 실패 0.
- ROADMAP, DECISIONS, AGENTS에 반복 작업 순서와 단계별 검증 기준 기록.

### 미완료·차단 사항
- 현재 작업 폴더에 Android 프로젝트가 없다. 외부 AndroidStudioProjects/PaletteJourney 존재만 확인했으며 수정하지 않았다.
- SDK platforms에는 android-37.0, build-tools에는 36.0.0이 있다. android-34/android.jar는 없다. 에뮬레이터 API34 이미지와 빌드용 플랫폼은 별개다.
- Gradle 전역 명령 없음. gradle-9.6.0-bin 캐시 폴더는 있으나 실행 가능한 gradle.bat는 발견하지 못했다. Gradle/AGP 호환 조합을 준비해 새 Android 프로젝트 빌드를 확인해야 한다.
- Android assemble/install/launch, 차량 SDK API 호출은 아직 미검증.
- GitHub URL 수신 후 하위 작업 폴더에 Git main 초기화 및 origin 설정 완료: https://github.com/dong-min-ss/Palette-Journey.git.
- GitHub 연결 도구의 저장소 조회는 404, 인증 없는 Git 조회는 Username 인증 필요로 실패했다. 원격 브랜치/내용을 아직 확인하지 못해 커밋·푸시는 보류한다. 사용자 Git 작성자 이름/이메일 설정도 확인되지 않았다.
- 서버, 모바일 웹, QR 연결, 영속 저장·동기화는 후속 구현 항목. 현재 순수 함수는 서버의 원자적 차감이나 계정 중복 생성 방지를 보장하지 않는다.
- 상시 실행/알림 자동화는 구성되지 않았다. 현재 채팅에서 검증 후 다음 기능을 진행하는 규칙을 마련한 상태다.

### 다음 작업
1. 호환 Gradle/AGP 및 빌드 SDK 준비 후 이 폴더에 독립 Android 앱 생성·빌드.
2. 서버 영속 계정 생성과 최초 지급 중복 방지 테스트, 만료 세션 API 구현.
3. GitHub 저장소 접근 권한/인증 및 커밋 작성자 확인 후 원격 내용을 fetch하고 기존 이력과 통합하여 첫 기능 커밋·푸시.
