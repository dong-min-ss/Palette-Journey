# Pleos 위치·차량 상태 API 검증

## 확인한 공식 경로
- 차량 위치: `ai.pleos.playground:FusedLocation:1.1.3`, `FusedLocation.isSupported()` 확인 후 initialize / getLastKnownLocation / registerFusedLocationCallback / unregister / release.
- 권한: `pleos.car.permission.FUSED_LOCATION`.
- 속도: AAOS `CarPropertyManager` / `VehiclePropertyIds.PERF_VEHICLE_SPEED`, m/s. `android.car.permission.CAR_SPEED`.
- 기어: AAOS `CarPropertyManager` / `VehiclePropertyIds.GEAR_SELECTION`. `android.car.permission.CAR_POWERTRAIN`.
- 문서 예제는1.1.2이나 공식 Maven metadata에는1.0.4와1.1.3만 공개되어1.1.3을 고정했다(2026-11-02 확인).

## 진단 도구
Android 차량 가족 연결 화면의 **위치·속도·기어 API 진단**. 위치와 SDK 시각/confidence, 속도·기어 조회 결과를 표시한다. SDK 오류·미지원·좌표 없음은 별도 표시한다. 좌표를 서버에 전송하거나 보상을 지급하지 않는다. 화면 이탈 시 구독/SDK/차량 연결을 해제한다.

이 단계는 읽기 API 가용성 확인이다. 속도0만으로 안전한 정차라고 판단하지 않는다. 실제 운전자 조작 허용에는 기어/신호 가용성/최신성 및 CarUxRestrictions 통합 검증이 필요하다. 기존 실차·릴리스 조작 잠금을 해제하지 않는다. 에뮬레이터 값은 시뮬레이션이며 실차 검증이 아니다.

## 공식 자료
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro
- https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup
- https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/maven-metadata.xml
- https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api
- https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq
- https://developer.android.com/reference/android/car/VehiclePropertyIds

## 2026-11-02 검증 결과
- Android assembleDebug 성공, 기존 단위 테스트18개 성공.
- Pleos_Connect_v2.1.0/API34/emulator-5554: VehicleSignalsDeviceTest.readOnlyPlatformSignals 실제1개 실행, failures=0/errors=0,0.860초. FusedLocation 지원=true, null이 아닌 좌표와 위경도 범위, finite 속도, 유효한 기어 값 읽기 확인.
- 최초 CAR_SPEED SecurityException 확인 후 런타임 권한 요청을 구현했다. 실제 기기 권한 조회에서 CAR_POWERTRAIN은 설치 시 허용, CAR_SPEED와 FUSED_LOCATION은 런타임 허용 필요를 확인했다. 기기 테스트는 UiAutomation으로 해당 읽기 권한을 허용한다.
- 최초 기기 부팅/패키지 관리 지연 및 차량 vendor 서비스 종료 로그가 있었으며, 데이터를 지우지 않고 에뮬레이터 재실행 후 부팅 완료를 기다려 성공했다. 실행0건/권한 거부 결과는 통과로 계산하지 않았다.
- 확인하지 않은 항목: 실차 수신, 좌표 최신성/시각 기준, confidence 임계값, 가상 GPS 이동에 따른 연속 좌표 변경, 기어 P↔D/속도 변경 시 전이, CarUxRestrictions 기반 조작 제한. 이는 다음 통합 단계에서 검증한다.
