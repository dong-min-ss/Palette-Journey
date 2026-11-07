# 시연용 APK

| 앱 | 파일 | 대상 |
| --- | --- | --- |
| 차량 앱 | [palette-vehicle-mvp.apk](palette-vehicle-mvp.apk) | Pleos Connect v2.1.0 에뮬레이터 |
| 폰 보조 앱 | [palette-companion-mvp.apk](palette-companion-mvp.apk) | Android 14(API 34) 이상 폰 에뮬레이터 |

[시연 가이드](../MVP_FLOW.md)에 설치와 서버 연결 절차가 있습니다. 두 파일은 에뮬레이터 시연용 디버그 APK입니다. [SHA-256 체크섬](SHA256SUMS)으로 다운로드한 파일을 확인할 수 있습니다.

Windows에서 다시 빌드하려면 `android` 폴더에서 `.\gradlew.bat :app:assembleDebug :companion:assembleDebug`를 실행합니다. 차량 APK는 `app/build/outputs/apk/debug/app-debug.apk`, 폰 APK는 `companion/build/outputs/apk/debug/companion-debug.apk`에 생성됩니다.
