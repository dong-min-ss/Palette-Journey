# MVP 보조 앱 APK

`palette-companion-mvp.apk`는 `android/:companion`의 개발용 자동 서명 APK다. Android 14/API 34 이상에 설치한다. PC 서버와 adb reverse가 필요한 폰 에뮬레이터 시연용이며 독립 오프라인 앱이 아니다. [실행·연결·녹음 순서](../PHONE_DEMO.md).

재생성: `./gradlew.bat :companion:assembleDebug`를 android 폴더에서 실행한 뒤 `companion/build/outputs/apk/debug/companion-debug.apk`를 이 파일로 복사한다. SHA-256은 `SHA256SUMS`에 기록한다. 개발용 서명 키는 저장소에 포함하지 않는다. 웹 화면은 실행 중인 PC 서버의 현재 소스를 사용한다.

차량용 앱은 별도 `:app` 모듈이다. `:app:assembleDebug` 결과 `android/app/build/outputs/apk/debug/app-debug.apk`를 차량 에뮬레이터에 설치한다.
