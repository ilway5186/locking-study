# 선착순 쿠폰 발급 시스템 - 2차 확장

## 1. 프로젝트 개요

이 프로젝트는 `Spring Boot + MySQL`만으로 동시성 제어를 어디까지 안전하게 다룰 수 있는지 학습하기 위한 토이 프로젝트다.

1차의 목표는 아래 두 문제를 분리해서 이해하는 것이었다.

- 총 발급 수량 초과 방지
- 같은 사용자 중복 발급 방지

2차의 목표는 여기서 한 단계 더 나아간다.

- 같은 API 요청의 재시도를 멱등하게 처리하기
- 요청 이력을 남겨 실패 사유와 통계를 분석하기
- DB 한 대에서 수량 제어 방식을 3가지로 비교하기
  - 1차 safe: 비관적 락
  - 2차 safe 비교: 조건부 UPDATE
  - 2차 실험: 낙관적 락 + 재시도

즉, 이번 단계의 핵심은 단순 기능 추가가 아니라 아래 질문에 답할 수 있게 만드는 것이다.

- 왜 사용자 중복 발급 방지와 API 멱등성은 다른 문제인가?
- 요청 이력이 왜 운영 통계와 장애 분석에 필요한가?
- 왜 조건부 UPDATE는 락 구간이 짧아질 수 있는가?
- 왜 낙관적 락은 충돌 감지 이후 재시도가 필요하다고 말하는가?
- 안전성과 병목 사이에서 어떤 trade-off가 생기는가?

## 2. 1차와 2차의 차이

### 1차에서 한 것

- `coupon_event`를 `PESSIMISTIC_WRITE`로 잠가 초과 발급 방지
- `coupon_issue(coupon_event_id, user_id)` unique index로 같은 유저 중복 발급 방지
- unsafe 비교 구현으로 oversell / same user duplicate 재현

### 2차에서 추가한 것

- `Idempotency-Key` 헤더 기반 멱등성
- `coupon_issue_request` 요청 이력 테이블
- 성공 / 실패 / 재사용 횟수 / 실패 사유 통계
- 조건부 UPDATE 비교용 safe 구현
- `@Version` 기반 낙관적 락 실험 구현
- 단위 / 통합 / 동시성 테스트 확장

### 이번 단계에서 가장 중요한 차이

1차는 "같은 유저가 두 번 발급받는가"를 막는 단계였다.  
2차는 "같은 HTTP 요청을 두 번 보냈을 때 같은 요청으로 볼 것인가"까지 다룬다.

둘은 전혀 같은 문제가 아니다.

- 사용자 중복 발급 방지: 비즈니스 규칙
- API 멱등성: 전송 재시도 / 네트워크 중복 / 클라이언트 재호출 제어

## 3. 현재 패키지 구조

```text
com.ilway.coupon
├─ common
│  ├─ api
│  └─ exception
├─ coupon
│  ├─ event
│  │  └─ api
│  └─ issue
│     ├─ api
│     └─ request
└─ comparison
   ├─ conditional
   ├─ optimistic
   └─ unsafe
```

### 왜 이렇게 나눴는가

- 기존 1차 운영 경로는 `coupon.issue` 아래에 유지했다.
- 요청 이력은 발급 도메인에 밀접하므로 `coupon.issue.request`로 붙였다.
- 비교용 구현은 `comparison` 아래로 분리해 차이가 보이게 했다.
- 공통화를 과하게 하지 않고, 전략별 흐름 차이가 코드에서 드러나도록 유지했다.

## 4. 1차 safe 구현 복습

기존 운영용 주력 경로는 그대로 유지했다.

흐름:

1. `coupon_event`를 `SELECT ... FOR UPDATE`로 조회
2. 발급 기간 검증
3. 같은 유저 중복 발급 여부 조회
4. `issued_quantity` 증가
5. `coupon_issue` 저장
6. 커밋 시 락 해제

장점:

- `조회 -> 검증 -> 변경` 흐름이 가장 직관적이다.
- 락 시점과 해제 시점을 설명하기 쉽다.
- 학습용으로 이해가 쉽고 디버깅도 편하다.

단점:

- 같은 이벤트에 대한 요청이 많아지면 직렬화가 심해진다.
- 이벤트 행 하나가 병목 지점이 되기 쉽다.

이번 2차에서도 public API의 기본 발급 경로는 여전히 이 방식을 사용한다.  
이유는 학습 목적상 가장 설명하기 쉽고, 멱등성/요청 이력/실패 사유 분기와 결합했을 때도 읽기 흐름이 가장 명확하기 때문이다.

## 5. DB 스키마

### coupon_event

```sql
create table coupon_event (
  id bigint auto_increment primary key,
  name varchar(100) not null,
  total_quantity int not null,
  issued_quantity int not null,
  start_at datetime(6) not null,
  end_at datetime(6) not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  version bigint not null
);
```

### coupon_issue

```sql
create table coupon_issue (
  id bigint auto_increment primary key,
  coupon_event_id bigint not null,
  user_id bigint not null,
  issued_at datetime(6) not null,
  constraint uk_coupon_issue_event_user unique (coupon_event_id, user_id),
  constraint fk_coupon_issue_event foreign key (coupon_event_id) references coupon_event(id)
);

create index idx_coupon_issue_user_id on coupon_issue(user_id);
create index idx_coupon_issue_event_id on coupon_issue(coupon_event_id);
```

### coupon_issue_request

```sql
create table coupon_issue_request (
  id bigint auto_increment primary key,
  idempotency_key varchar(100) not null,
  coupon_event_id bigint not null,
  user_id bigint not null,
  request_status varchar(20) not null,
  failure_reason varchar(50) null,
  issued_coupon_issue_id bigint null,
  reused_count int not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_coupon_issue_request_event_user_key
    unique (coupon_event_id, user_id, idempotency_key)
);

create index idx_coupon_issue_request_event_status
  on coupon_issue_request(coupon_event_id, request_status);

create index idx_coupon_issue_request_event_failure
  on coupon_issue_request(coupon_event_id, failure_reason);
```

### 왜 이 제약조건이 중요한가

- `coupon_issue` unique index: 같은 유저 중복 발급의 마지막 방어선
- `coupon_issue_request` unique index: 같은 사용자 + 같은 이벤트 + 같은 idempotency key를 하나의 논리 요청으로 묶음
- `not null`: 수량 / 시간 / 상태 컬럼 무결성 보장
- `event/status`, `event/failure` 인덱스: 관리자 통계 조회 시 집계 비용 감소

### 왜 request history는 coupon_event FK를 두지 않았는가

의도적으로 두지 않았다.

이유:

- `EVENT_NOT_FOUND` 같은 실패도 요청 이력에 남기고 싶다.
- 요청 로그는 비즈니스 엔터티보다 더 오래 남을 수 있다.
- 요청 이력은 "결과 분석용 로그" 성격이 강해서 참조 무결성보다 분석 가능성이 더 중요했다.

이 부분은 trade-off다.

- FK 장점: 참조 무결성 강화
- FK 단점: 존재하지 않는 이벤트 요청을 기록하기 어려움

이번 2차에서는 실패 분석 목적을 우선했다.

## 6. API

### 이벤트 생성

- `POST /api/coupon-events`

```json
{
  "name": "오픈 기념 이벤트",
  "totalQuantity": 100,
  "startAt": "2026-03-28T20:00:00",
  "endAt": "2026-03-28T21:00:00"
}
```

### 쿠폰 발급

- `POST /api/coupon-events/{couponEventId}/issues`
- Header: `Idempotency-Key` 선택 사항

```json
{
  "userId": 1
}
```

### 발급 응답 예시

```json
{
  "success": true,
  "data": {
    "issueId": 1,
    "couponEventId": 1,
    "userId": 1,
    "issuedAt": "2026-03-28T20:00:01",
    "resultType": "ISSUED"
  }
}
```

같은 키 재요청의 성공 재사용이면 `resultType`은 `REUSED`가 된다.

### 이벤트 조회

- `GET /api/coupon-events/{couponEventId}`

### 내 발급 이력 조회

- `GET /api/users/{userId}/coupon-issues`

### 관리자 통계 조회

- `GET /api/admin/coupon-events/{couponEventId}/statistics`

응답에는 아래가 포함된다.

- 성공 발급 수
- 총 논리 요청 수
- 총 시도 수
- 성공 요청 수
- 실패 요청 수
- 멱등 재사용 횟수
- 실패 사유별 집계

## 7. 왜 Header 기반 Idempotency-Key를 선택했는가

이번 2차에서는 `requestId`를 body에 넣지 않고 `Idempotency-Key` 헤더를 선택했다.

이유:

- 비즈니스 데이터(`userId`)와 전송 재시도 식별자(멱등성 키)를 분리할 수 있다.
- HTTP API에서 멱등 키는 헤더로 두는 편이 더 자연스럽다.
- 이후 결제 / 예약 API로 확장할 때도 같은 패턴을 가져가기 쉽다.
- 기존 request body를 깨지 않고 API를 확장할 수 있다.

추가로, 헤더가 없으면 서버가 내부적으로 임의 키를 생성해 요청 이력은 남긴다.  
다만 이 경우에는 "재시도 간 같은 키 보장"이 없으므로 멱등성 보장은 제공되지 않는다.

즉:

- 헤더 있음: 멱등성 사용
- 헤더 없음: 기존 API 호환 유지, 단순 요청 이력만 기록

## 8. 멱등성 정책

이번 단계에서는 정책을 단순하게 고정했다.

### 정책

- 같은 `couponEventId + userId + idempotencyKey`
  - 처음 요청: 새 요청으로 처리
  - 이전 성공 존재: 같은 성공 결과 재사용
  - 이전 실패 존재: 같은 실패 결과 재사용
  - 아직 처리 중: `DUPLICATE_REQUEST_IN_PROGRESS` 반환

### 왜 실패도 재사용하는가

이번 2차의 초점은 멱등성 보장과 이력 분석이다.  
실패한 같은 키를 다시 새로 처리하게 열어 두면 "같은 논리 요청"의 경계가 흐려진다.

그래서 이번 프로젝트는 가장 단순한 정책을 채택했다.

- 같은 키는 항상 같은 논리 요청
- 성공도 재사용
- 실패도 재사용

이 정책은 실무의 모든 상황에 정답은 아니다.  
예를 들어 결제 시스템에서는 `IN_PROGRESS`, `FAILED`, `CONFIRMED`, `COMPENSATED` 같은 더 복잡한 상태 전이가 필요할 수 있다.  
하지만 학습용 2차에서는 이 단순 정책이 가장 이해하기 쉽다.

## 9. 요청 이력 테이블이 왜 필요한가

`coupon_issue_request`는 단순 로그 테이블이 아니다.

이 테이블은 동시에 3가지 역할을 한다.

1. 중복 요청 판별
- 같은 key의 중복 insert를 unique constraint로 차단

2. 결과 재사용
- 이미 성공한 요청이면 같은 발급 결과를 다시 반환
- 이미 실패한 요청이면 같은 실패를 다시 반환

3. 운영 통계 / 장애 분석
- 실패 건수 집계
- 실패 사유 집계
- 멱등 재사용 횟수 집계

1차에서는 성공 발급만 셀 수 있었다.  
2차에서는 "왜 실패했는지"를 관리자 통계로 올릴 수 있게 됐다.

## 10. 실패 사유 분류

현재 요청 이력에서 관리하는 실패 사유는 아래와 같다.

- `EVENT_NOT_FOUND`
- `NOT_IN_ISSUE_PERIOD`
- `ALREADY_ISSUED`
- `SOLD_OUT`
- `CONFLICT_RETRY_EXCEEDED`
- `INTERNAL_ERROR`

별도로 API 에러 코드에는 아래도 존재한다.

- `DUPLICATE_REQUEST_IN_PROGRESS`

이 코드는 "같은 멱등 키 요청이 아직 끝나지 않음"을 뜻한다.  
논리 요청 자체가 최종 실패한 것은 아니므로, 현재 요청 이력의 terminal failure reason에는 포함하지 않았다.

## 11. 세 가지 수량 제어 방식 비교

### A. 비관적 락

핵심 쿼리:

```sql
select *
from coupon_event
where id = ?
for update;
```

특징:

- 락을 먼저 잡고 검증과 변경을 모두 수행
- 같은 이벤트 행 기준으로 직렬화

### B. 조건부 UPDATE

핵심 쿼리:

```sql
update coupon_event
set issued_quantity = issued_quantity + 1,
    version = version + 1
where id = ?
  and issued_quantity < total_quantity;
```

특징:

- 수량 증가 자체를 원자적 UPDATE 한 번으로 처리
- 락을 오래 쥐는 대신 UPDATE 결과 행 수로 성공/실패를 해석

### C. 낙관적 락

핵심 아이디어:

- `coupon_event.version`으로 충돌 감지
- 읽은 뒤 수정하다가 다른 트랜잭션이 먼저 커밋하면 optimistic lock 예외 발생
- 서비스 계층에서 재시도

## 12. 세 방식 비교표

| 항목 | 비관적 락 | 조건부 UPDATE | 낙관적 락 |
| --- | --- | --- | --- |
| 수량 초과 방지 안전성 | 높음 | 높음 | 높음(재시도 전제) |
| 같은 유저 중복 방지 | `coupon_issue` unique index | `coupon_issue` unique index | `coupon_issue` unique index |
| 멱등성 결합 난이도 | 가장 쉬움 | 중간 | 가장 어려움 |
| 락/충돌 발생 시점 | 조회 시점부터 락 | UPDATE 시점에 짧게 | flush/commit 시 optimistic conflict |
| 병목 가능성 | 높음 | 중간 | 충돌 많으면 재시도 비용 큼 |
| 코드 가독성 | 가장 좋음 | 분기 해석이 늘어남 | 재시도까지 포함되면 복잡 |
| 테스트 난이도 | 비교적 쉬움 | 실패 해석 검증 필요 | 충돌 재현과 재시도 검증 필요 |
| 실무 확장성 | 단일 DB hot row에 약함 | 고부하 재고 차감에 유리할 수 있음 | read-heavy / 충돌 적은 경우 적합 |

### 요약

- 학습용으로 가장 이해하기 쉬운 방식: 비관적 락
- 고부하 hot row에서 더 유리할 수 있는 방식: 조건부 UPDATE
- 충돌이 적고 재시도 관리가 가능할 때 실험 가치가 있는 방식: 낙관적 락

## 13. 왜 조건부 UPDATE가 락 구간이 짧다고 말하는가

비관적 락은 `SELECT ... FOR UPDATE` 이후 커밋까지 이벤트 행을 잡는다.

반면 조건부 UPDATE는 실제 공유 자원 변경을 아래 한 문장에 집중시킨다.

```sql
update coupon_event
set issued_quantity = issued_quantity + 1
where id = ?
  and issued_quantity < total_quantity;
```

이 말은 "락이 아예 없다"는 뜻이 아니다.  
DB는 UPDATE 동안 필요한 잠금을 사용한다.  
다만 애플리케이션 관점에서는 `조회 -> 검증 -> 저장` 전체를 길게 잠그지 않고, 공유 숫자 증가를 SQL 한 번으로 원자화한다는 점이 다르다.

### 대신 복잡해지는 점

- UPDATE 결과가 0일 때 왜 실패했는지 해석 로직이 필요하다
- 기간 검증은 별도로 해야 한다
- 사용자 중복 발급은 여전히 다른 제약으로 막아야 한다
- 멱등성/요청 이력까지 결합하면 분기 설명이 길어진다

즉, 성능 여지가 늘어나는 대신 코드 설명은 어려워진다.

## 14. 왜 낙관적 락은 재시도가 필요한가

낙관적 락은 충돌을 막는 방식이 아니라 감지하는 방식이다.

예를 들어:

1. 트랜잭션 A가 version 3 읽음
2. 트랜잭션 B도 version 3 읽음
3. A가 먼저 커밋해서 version 4가 됨
4. B가 커밋하려 하면 "내가 읽은 version 3이 더 이상 유효하지 않다"며 실패

따라서 낙관적 락은 보통 아래가 같이 따라온다.

- optimistic lock 예외
- 재시도 정책
- 재시도 횟수 제한

이번 프로젝트는 가장 단순하게 `3회 재시도` 정책을 사용했다.  
3회 안에 해결되지 않으면 `CONFLICT_RETRY_EXCEEDED`로 실패 처리한다.

### 왜 1차에서는 쓰지 않았고 2차에서 실험했는가

1차의 핵심은 "락이 왜 필요한지"를 먼저 이해하는 것이었다.  
낙관적 락은 충돌 감지 이후 재시도까지 같이 설명해야 하므로 초기에 학습 부담이 크다.

2차에서는 이미 1차의 안전한 기준점을 확보했기 때문에 비교 실험 가치가 생긴다.

## 15. 운영용 주력 방식은 무엇으로 봤는가

현재 프로젝트의 기본 운영 경로는 여전히 `비관적 락 + 요청 이력 + unique index`다.

이유:

- 가장 이해하기 쉽다
- 멱등성 / 실패 분기 / 관리자 통계와 결합했을 때 흐름이 가장 명확하다
- 학습용으로 "왜 안전한가"를 설명하기 쉽다

다만 실무에서 hot event가 심한 경우에는 조건부 UPDATE 방식이 더 유리할 수 있다.  
그래서 이번 2차에서는 조건부 UPDATE를 "비교용 safe 구현"으로 반드시 추가했다.

즉, 현재 판단은 아래와 같다.

- 이 프로젝트의 주력 설명 경로: 비관적 락
- 성능 관점에서 더 검토할 가치가 큰 방식: 조건부 UPDATE
- 실험/비교 가치가 큰 방식: 낙관적 락

## 16. 관리자 통계에서 무엇을 볼 수 있게 되었는가

1차:

- 성공 건수
- 발급 수량
- 잔여 수량

2차:

- 총 논리 요청 수
- 총 시도 수
- 성공 요청 수
- 실패 요청 수
- 실패 사유별 건수
- 멱등 재사용 횟수

이 차이가 중요한 이유는, 운영에서는 성공 수만 보는 것으로는 문제가 안 보이기 때문이다.

예를 들어:

- 실패가 갑자기 `SOLD_OUT`으로 몰리는지
- `ALREADY_ISSUED`가 비정상적으로 많은지
- 멱등 재사용이 급증하는지
- 낙관적 락에서 `CONFLICT_RETRY_EXCEEDED`가 늘어나는지

이런 지표가 있어야 병목과 UX 문제를 함께 볼 수 있다.

## 17. 테스트 시나리오

### 단위 테스트

- 요청 이력 상태 전이
- 실패 사유 분류
- idempotency claim 정책
- 조건부 UPDATE 실패 해석
- 낙관적 락 재시도 정책

### 통합 테스트

- 정상 발급 + 요청 이력 저장
- 같은 idempotency key 재요청 시 성공 결과 재사용
- 다른 key + 같은 userId 시 중복 발급 실패
- 요청 이력 기반 관리자 통계 조회
- 조건부 UPDATE 버전 정상 발급
- 낙관적 락 버전의 중복 발급 실패 처리

### 동시성 테스트

- 같은 userId + 같은 idempotencyKey 100번 동시 요청
- 서로 다른 userId 1000명 동시 요청
- 비관적 락 vs 조건부 UPDATE 결과 비교
- unsafe oversell / duplicate 재현
- 낙관적 락 충돌 및 재시도 초과 검증

### 테스트 하네스에서 보강한 점

- 가상 스레드를 사용해 동시성 테스트의 실제 시작 장벽을 맞췄다
- `@DirtiesContext(AFTER_CLASS)`를 넣어 Testcontainers MySQL과 Spring context 재사용 충돌을 피했다

## 18. 로컬에서 검증한 명령

`2026-03-28` 기준 아래 명령으로 전체 테스트를 실행했다.

```bash
./gradlew test
```

현재 환경에서는 Docker Desktop이 켜져 있어 Testcontainers 기반 통합 / 동시성 테스트까지 포함해 모두 통과했다.

## 19. 왜 비교용 엔드포인트를 따로 만들지 않았는가

이번 2차에서는 `/issues/pessimistic`, `/issues/conditional`, `/issues/optimistic` 같은 외부 엔드포인트를 추가하지 않았다.

이유:

- public API를 전략별 실험용 URL로 오염시키고 싶지 않았다
- 실제 비교 포인트는 서비스 / 테스트 계층에서 충분히 드러난다
- 현재 프로젝트의 주력 운영 경로는 비관적 락 하나로 유지하는 편이 명확하다

대신 비교 구현은 내부 서비스와 동시성 테스트에서 나란히 검증한다.

## 20. 이번 2차에서 새로 배운 점

1. 같은 유저 중복 발급과 API 멱등성은 다르다
2. 요청 이력이 있어야 실패 사유와 재사용 횟수를 통계로 볼 수 있다
3. 조건부 UPDATE는 락 구간이 짧을 수 있지만 코드 설명이 더 어려워진다
4. 낙관적 락은 충돌 감지 후 재시도까지 설계해야 완성된다
5. 동시성 제어 방식이 달라지면 테스트 전략도 같이 달라진다
6. 새로운 동시성 장치를 추가하면 기존 비교용 경로에 부작용이 생길 수 있다
   - 예: `@Version` 추가 후 unsafe 경로가 의도치 않게 안전해지는 문제
   - 예: reused count 증가와 status update가 서로 덮어쓰는 문제

## 21. 좌석 예매 / 결제 / 재고 시스템으로 확장될 때 이어지는 포인트

이번 2차는 그 자체로 끝이 아니라, 좌석 예매나 결제 시스템의 입구다.

### 좌석 예매

- 좌석 한 개를 누가 선점했는지
- 같은 예약 요청 재시도를 같은 요청으로 처리할지
- 선점 실패 / 만료 / 취소 상태를 어떻게 관리할지

### 결제

- 같은 결제 요청이 중복 전송되어도 1번만 승인되어야 함
- idempotency key와 request history는 거의 필수
- `IN_PROGRESS -> SUCCESS/FAILED/COMPENSATED` 같은 더 복잡한 상태 전이 필요

### 재고 차감

- hot item 재고 차감은 조건부 UPDATE 패턴이 자주 검토됨
- 품절 직전 충돌과 실패 사유를 어떻게 분류할지 중요

즉, 이번 2차의 멱등성 / 요청 이력 / 조건부 UPDATE / 낙관적 락 비교는 그대로 다음 도메인으로 이어진다.

## 22. 면접에서 말할 수 있는 포인트

- 1차에서는 비관적 락과 unique index로 oversell과 duplicate issue를 분리해서 해결했다
- 2차에서는 idempotency key와 request history를 도입해 API 재시도 문제를 별도로 다뤘다
- 요청 이력을 이용해 실패 사유 집계와 멱등 재사용 횟수 통계를 만들었다
- 같은 DB 한 대에서 비관적 락, 조건부 UPDATE, 낙관적 락을 비교했고 trade-off를 테스트로 검증했다
- `@Version` 같은 추가 안전장치가 기존 unsafe 비교 경로를 오염시키는 문제까지 겪었고, 이를 비교 실험 관점에서 다시 분리했다

## 23. 한 줄 요약

`Spring Boot + MySQL 기반 선착순 쿠폰 발급 시스템을 2차까지 확장하면서, 비관적 락/조건부 UPDATE/낙관적 락을 비교하고, Idempotency-Key와 요청 이력 테이블로 API 재시도와 실패 통계까지 다룬 프로젝트`
