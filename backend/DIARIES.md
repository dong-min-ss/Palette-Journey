# 가족 여행 그림일기 저장 모델

## 소유 관계

현재 게스트 계정1개를 가족1개로 매핑한다. families → children/trips → diaries → pages 관계다.
일기는 자녀1명과 여행1개에 속하며 페이지는 해당 일기 안에서 순서를 가진다.
가족 비공개이며 공개 API나 공개 링크는 없다. 현재 같은 가족의 세션 참여자는 가족 전체 기록을 편집할 수 있다.
보호자 로그인·자녀별 접근 권한·공개 승인 기능은 아직 구현하지 않았다. 별명만 입력하며 실명·정확한 위치를 요구하지 않는다.

## 기존 작품 보존

첫 library 조회 때 트랜잭션으로 기본 가족/별명/여행/일기/첫 페이지를 만든다.
기존 artwork 획·이전 영역 바탕을 첫 페이지로 복사한다. 원본 artworks 행은 수정하거나 삭제하지 않는다.
같은 계정 재접속·재시작·동시 초기화로 중복 생성되지 않는다.
전환 후 새 웹은 pages에 저장한다. 구형 artwork API와 원본은 호환용이며 페이지 변경을 원본에 역동기화하지 않는다.

## API

모든 요청은 기존 참여자 Bearer 토큰과 유효 세션이 필요하다.

| 메서드/경로 | 입력 또는 결과 |
|---|---|
| GET /api/session/library | 가족ID, children, trips, diaries, 페이지 목록 |
| POST /api/session/children | {name: 별명} (1~40자) |
| POST /api/session/trips | {title: 여행 이름} |
| POST /api/session/diaries | {title, childId, tripId} |
| POST /api/session/diaries/{id}/pages | {title, template: blank 또는 landscape} |
| GET /api/session/pages/{id} | {id, diaryId, position, title, text, template, strokes, legacyRegions, revision} |
| PUT /api/session/pages/{id} | {title, text, template, strokes, revision} |

제목은1~80자, 글은0~2000자다. 글만 있거나 그림만 있어도 저장할 수 있다.
도안은 페이지 생성 시 고르며 저장 요청으로 바꿀 수 없다. 획 검증은 core/artwork.mjs를 사용한다.
구형 바탕은 서버의 기존 데이터를 유지한다. 클라이언트 값으로 임의 변경하지 않는다.
타 가족ID 접근은404, 세션 만료는401, 잘못된 입력은400, 같은 페이지 버전 충돌은409다.
버전 비교와 갱신을 같은 트랜잭션으로 수행한다. 다른 페이지의 저장은 별도 버전이다.

## 웹 저장 흐름

그림·제목·글 변경 시 페이지별 localStorage 임시 기록을 쓰고1.2초 입력 중지 후 서버에 저장한다.
페이지를 바꿀 때는 이전 페이지의 저장 완료를 기다린다. 실패하면 자동 전환하지 않는다.
서버 저장 실패 시 임시 기록을 유지한다. 같은 버전이면 다시 열 때 복원하고,
서버가 앞서 있으면 서버 내용을 표시하며 사용자가 명시적으로 임시 기록으로 이어쓰기를 선택할 수 있다.
저장 중 추가 변경은 새 버전으로 다시 저장한다. 충돌 후에는 자동 재시도를 멈춘다.
현재 기기 임시 기록은 해당 브라우저 저장소에만 있으며 데이터 삭제 시 사라진다.

스티커·음성·방문 지역·여행책 감상·공개 출품은 아직 페이지 API에 연결하지 않았다.
