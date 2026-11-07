# Pleos 위치·차량 상태 API 검증

## 확인한 공식 경로
- 차량 위치: `ai.pleos.playground:FusedLocation:1.1.3`, `FusedLocation.isSupported()` 확인 후 initialize / getLastKnownLocation / registerFusedLocationCallback / unregister / release.
- 권한: `pleos.car.permission.FUSED_LOCATION`.
- 속도: AAOS `CarPropertyManager` / `VehiclePropertyIds.PERF_VEHICLE_SPEED`, m/s. `android.car.permission.CAR_SPEED`.
- 기어: AAOS `CarPropertyManager` / `VehiclePropertyIds.GEAR_SELECTION`. `android.car.permission.CAR_POWERTRAIN`.
- 문서 예제는1.1.2이나 공식 Maven metadata에는1.0.4와1.1.3만 공개되어1.1.3을 고정했다(2026-11-02 확인).

## 진단 도구
Android 차량 가족 연결 화면의 **위치·속도·기어 API 진단**. 위치와 SDK 시각/confidence, 속도·기어 조회 결과를 표시한다. SDK 오류·미지원·좌표 없음은 별도 표시한다. 기본은 읽기 진단이며, DEBUG 에뮬레이터에서 사용자가 GPS 보상 시연을 시작하면 가족 세션으로 좌표를 전송한다. 화면 이탈 시 구독/SDK/차량 연결을 해제한다.

읽기 API와 에뮬레이터 보상 시연을 제공한다. 속도0만으로 안전한 정차라고 판단하지 않는다. 실제 운전자 조작 허용에는 기어/신호 가용성/최신성 및 CarUxRestrictions 통합 검증이 필요하다. 기존 실차·릴리스 조작 잠금을 해제하지 않는다. 에뮬레이터 값은 시뮬레이션이며 실차 검증이 아니다.

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
- 확인하지 않은 항목: 실차 수신, confidence 임계값, 실차 좌표 시각 보장, CarUxRestrictions 기반 조작 제한. GPS·기어·속도 시뮬레이션 및 시연용 최신성/보상 연결은 아래 후속 결과를 따른다.

## GPS 기반 에뮬레이터 보상 시연
- 앱 첫 화면의 **가족 연결**에서 기존 가족에 연결한 뒤 → **개발용 시연 → 위치·속도·기어 API 진단** → **GPS 보상 시연 시작·중지**. 에뮬레이터 DEBUG에서만 시작 버튼이 활성화된다.
- Pleos SDK의 좌표/측정 시각을5초마다 `/api/session/visits/vehicle`로 전송한다. 서버의 PALETTE_MOCK_LOCATION=1 필요. 실차 보상 인증 경로가 아니다.
- 서버 수신 시각으로120초 체류를 계산한다. SDK 위치가30초 넘게 오래됐거나5초 넘게 미래면 체류를 초기화한다. 위치 없음/화면 이탈/명시적 중지도 초기화한다. 통신이30초 넘게 끊기면 다음 표본에서 초기화한다.
- 가속 버튼의 모의 시각과 차량 GPS의 서버 시각은 서로 다른 진행 이력으로 저장한다. 같은 가족의 simulation 보상 영수증은 공유하여 양쪽 경로로 중복 지급하지 않는다.
- 최신성 기준은 이번 시연용이다. 좌표의 진위/기기 인증, confidence 의미/임계값, 실제 행정구역/운영 체류 조건은 실차 연동 전에 별도 확정한다.

### 가상 입력
GPS: `adb -s emulator-5554 emu geo fix 129.219 37.0`은 경주 밖, `adb -s emulator-5554 emu geo fix 129.219 35.8347`은 시연 구역 안이다. 지속 체류 시험 중에는10초마다 좌표를 갱신한다.
기어/주행: 공식 Virtual Console을 사용한다. `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/ 에서 D/ACCEL/P를 조작한다. 이 이미지의 일반 car_service 주입 명령은 적용되지 않았고 cmd car_service는 non-user build 요구로 거부됐다.
출처: https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator

## 2026-11-02 후속 통합 결과
- 경주 밖 위도37.0/P, 경주 안 위도35.8347/D, D+가속 중1m/s 이상 속도, P 복원 후 읽기 기기 테스트 각각1개 통과. Virtual Console에서 P/속도0 복원 확인. 가속을 짧게 누른 후 속도가0으로 줄어든 시험은 가속 유지 중 재실행하여 검증했다.
- 실제 Pleos SDK 좌표 → Android HTTP → 서버 수신 시간 기반120초 체류 → 동일 가족의 별도 참여자 팔레트/카탈로그 경로 통과. GpsVisitDeviceTest 결과 tests=1 failures=0/errors=0,123.580초. 합성 가족만 사용했고 시험 세션은 취소했다.
- 서버 전체 테스트49개, Android 기존 단위 테스트18개 통과. 가속 경로 시각과 GPS 입력 격리, 오래된 좌표·좌표 없음 초기화, 무료/중복 없는 가족 보상 유지.
- 실차 및 실제 휴대폰 화면 표시 검증과는 구분한다. 에뮬레이터 입력·패킷은 실방문 증거가 아니므로 모두 simulation 보상으로 남긴다.

## 진단 화면의 임시 표시 설정
개발용 에뮬레이터에서 **위치·속도·기어 API 진단** 화면만 D 상태에서도 표시하도록 debug 전용 Manifest를 적용했다. 가족 연결/다른 화면은 P에서 연다. 진단에 들어간 다음 D/ACCEL로 시험하고 P로 복귀한다. release에는 이 설정이 없다. 속도·기어 이력은 저장하지 않으며 현재 값만 표시한다.

## 진단 화면 갱신
속도·기어 표시는 CarPropertyManager 변경 이벤트를 구독한다. 수신 횟수/마지막 수신 경과 시간을 함께 표시하며 값이 변하지 않으면 경과 시간이 증가할 수 있다. 이 정보는 화면 내 진단 정보이며 영속 이력은 아니다. 실제 Activity를 열린 채 유지한 D/가속/P 표시 전이 기기 테스트를 추가했다.

### 임시 예외 보완
개발용 빌드는 실제 진입 경로인 첫 화면/가족 연결/진단 모두 임시 가림 예외를 적용한다. 기존의 진단 화면만 예외라는 설명을 대체한다. release에는 이 설정이 없으며 실제 버튼 경로의 D 주행 화면 캡처로 가림 없음을 확인했다.

## 개발용 화면 표시
진단은 속도(km/h)/기어 문자+원시값/GPS/방문 보상 카드로 구분한다. 콘솔과 단위를 맞춰 속도를 비교할 수 있다. 원래 m/s·SDK 시각·confidence·수신 횟수는 **SDK·수신 상세 펼치기**로 확인한다. 첫 화면과 가족 연결에도 고대비 버튼·입력·글자 스타일을 적용했다.

## 잠금 상태부터 직접 시연하기
1. 앱 첫 화면 → **가족 연결** → **개발용 시연** → **새 시연 가족 · 지역 잠금 상태**를 누른다. 이전 가족의 자격증명·콘텐츠는 그대로 두고 별도 시연 가족으로 전환한다. 시연 가족에서는 서버의 개발용 스티커 해금을 제외한다.
2. **위치·속도·기어 API 진단**에서 경주 색0/3·스티커0/2와 다섯 항목의 잠김 표시를 확인한다.
3. GPS를 경주 밖 위도37.0/경도129.219로 설정하고 **GPS 보상 시연 시작·중지**를 누른다.
4. GPS를 경주 안 위도35.8347/경도129.219로 옮긴 뒤10~15초마다 갱신한다. 서버 수신 시간120초 체류 뒤 목록이 색3/3·스티커2/2/해금됨으로 바뀐다. D 주행은 필요하지 않다.
5. **가족 연결**로 돌아와 필요하면 같은 가족용 휴대폰 QR을 새로 발급한다. **기존 가족으로 돌아가기**는 시연 전 저장한 일반 가족 연결로 돌아간다. 기존 세션이 만료됐다면 새 연결 코드가 필요하다.

시연 자격증명은 별도 Keystore 암호화 슬롯에 저장한다. 일반 가족의 개발용 설정/보유 기록/초대 세션을 삭제하거나 변경하지 않는다. 새 시연 가족 버튼을 다시 누르면 새로운 잠금 상태 가족을 만들며 기존 시험 보상은 지우지 않는다.

## 5도시 모의 방문 구역
기존 경주 시연을 유지하면서 다음 구역을 추가했다. 전부 시연 중심 반경5km/연속120초/표본 공백30초 이내 조건이며 실제 도시 경계나 운영 방문 정책이 아니다. 서로 다른 도시의 체류는 합산하지 않는다. 각 도시 보상은 가족별1회, 색3개·스티커2개이며 티켓은 차감/추가 지급하지 않는다.

| 도시 | 시연 위도 | 시연 경도 |
|---|---:|---:|
| 경주 | 35.8347 | 129.219 |
| 서울 | 37.5796 | 126.977 |
| 부산 | 35.1532 | 129.1186 |
| 제주 | 33.5117 | 126.5219 |
| 강릉 | 37.7736 | 128.948 |

진단 화면은 수신한 구역의 이름과 체류/수집 결과를 표시한다. 지역별 전체 콘텐츠와 현재 가족 보유 여부는 **색·스티커 사전**에서 본다. 웹의 기존 가속 모의 경로 버튼은 계속 경주 전용이다. 다른 도시 GPS 시험은 해당 시연 좌표를 에뮬레이터 Location의 Search에 넣고 Set Location으로 전송한다.


## 콘솔 다도시 주행 시연
차량 첫 화면 **개발 도구 → 개발자 주행 시연**에서 도시 선택→콘솔 D·ACCEL로300m→P·정지5초로 해금을 확인한다. **지역 방문 초기화**는 같은 시연 가족의 지역 보상을 다시 잠그고 가족 연결·그림·대화는 유지한다. [전체 버튼 순서](../../DRIVE_DEMO.md). 기존 GPS·120초 체류 경로는 별도로 유지한다.
