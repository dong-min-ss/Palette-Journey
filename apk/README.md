# 제출용 release APK

| 앱 | 파일 | 대상 |
| --- | --- | --- |
| 차량 앱 | [박동민_PaletteJourney_release.apk](박동민_PaletteJourney_release.apk) | Pleos Connect v2.1.0 에뮬레이터, 2560 × 1440px |
| 폰 보조 앱 | [박동민_PaletteJourneyCompanion_release.apk](박동민_PaletteJourneyCompanion_release.apk) | Android 폰 에뮬레이터, 최소 API 34 |

두 APK는 별도 제출용 키로 서명한 release 빌드입니다. `debuggable=false`이며 WebView 원격 디버깅을 끕니다. 차량 시연은 빌드 옵션과 에뮬레이터 하드웨어 확인을 함께 통과해야 합니다. 실차 배포용 APK는 아닙니다. [SHA-256 체크섬](SHA256SUMS)으로 파일을 확인할 수 있습니다.

[설치·서버 연결·시연 가이드](../MVP_FLOW.md)와 [검증 환경](../README.md#테스트-환경과-필요한-설정)을 참고하세요. PC 서버가 필요하며, 차량은 `10.0.2.2:3000`, 폰은 ADB reverse를 통한 `localhost:3000`으로 연결합니다. 일반 HTTP 호스트는 차량 앱에서 허용하지 않습니다.

제출용 패키지는 차량 `com.palettejourney.submission`, 폰 `com.palettejourney.companion.submission`입니다. 기존 debug 앱의 데이터와 분리되며 새로 가족을 연결해야 합니다. 앱 업데이트는 같은 서명 키를 사용합니다.

## 다시 빌드하기

Windows, JDK 21, Android SDK / compile SDK 37과 Gradle 의존성을 준비하고, 저장소 루트에서 실행합니다.

```powershell
./scripts/Build-Submission.ps1 -TeamName '박동민'
```

처음 실행하면 `android/.signing/`에 제출용 RSA 키와 로컬 비밀번호 파일을 만듭니다. 이후 같은 키를 재사용합니다. 해당 폴더는 Git에서 제외되므로 비공개로 백업하세요. 키와 비밀번호를 저장소에 업로드하지 마세요. 다른 PC에서 새 키를 만들면 기존 APK를 덮어 설치할 수 없습니다.

스크립트는 `:app:assembleRelease :companion:assembleRelease -PpaletteSubmissionDemo=true`를 실행하고 서명된 APK 두 개와 체크섬을 `apk/`에 복사합니다. 키 생성 후 Gradle만 직접 실행해도 같은 결과물을 빌드할 수 있습니다. `paletteSubmissionDemo` 옵션을 생략한 일반 release에는 차량 시연 기능과 로컬 HTTP 예외를 활성화하지 않습니다.

기존 개발용 debug 빌드는 `android` 폴더에서 `./gradlew.bat :app:assembleDebug :companion:assembleDebug`로 생성합니다. 제출 파일은 위 release APK입니다.
