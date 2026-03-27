# 선착순 쿠폰 발급 시스템

## 1. 프로젝트 개요

이 프로젝트는 `Spring Boot + MySQL`만으로 어디까지 안전한 동시성 제어가 가능한지 학습하기 위한 토이 프로젝트다.

핵심 주제는 두 가지다.

- 총 발급 수량을 절대 초과하지 않게 막기
- 같은 사용자가 같은 이벤트에서 중복 발급받지 못하게 막기

이 둘은 비슷해 보이지만 실제로는 다른 문제다.

- 수량 초과 방지는 `공유 자원(coupon_event)`에 대한 동시 수정 문제다.
- 중복 발급 방지는 `중복 데이터(coupon_issue)` 생성 문제다.

그래서 최종 구현은 아래 조합을 사용했다.

- 수량 초과 방지: `SELECT ... FOR UPDATE` 기반 비관적 락
- 사용자 중복 발급 방지: `coupon_issue(coupon_event_id, user_id)` unique index

현재 프로젝트는 처음에 거의 빈 Spring Boot 골격이었고, 기존 구조를 갈아엎지 않고 그 위에 필요한 최소 구조만 추가했다.

- 기존 상태: `CouponApplication`, 기본 `build.gradle`, `application.yaml`
- 추가한 것: `Web/JPA/MySQL/Validation/Testcontainers`, 도메인/API/테스트/README

## 2. 이 프로젝트로 배울 수 있는 것

- `@Transactional`만 붙인다고 동시성 문제가 해결되지 않는 이유
- 일반 조회와 `SELECT ... FOR UPDATE`의 차이
- 비관적 락이 정확히 언제 걸리고 언제 풀리는지
- unique index가 중복 발급 방지에서 왜 중요한지
- 수량 차감 문제와 중복 발급 문제가 왜 분리되어야 하는지
- 단위 테스트 / 통합 테스트 / 동시성 테스트를 어떻게 나눠야 하는지
- Spring + MySQL 조합의 장점과 한계

## 3. 핵심 비즈니스 규칙

1. 쿠폰 이벤트에는 총 발급 수량이 있다.
2. 한 사용자는 같은 이벤트에서 쿠폰을 최대 1개만 발급받을 수 있다.
3. 동시에 많은 요청이 들어와도 총 발급량을 초과하면 안 된다.
4. 중복 발급이 발생하면 안 된다.
5. 발급 시작 시간 전 / 종료 시간 후에는 발급되면 안 된다.
6. 실패 사유는 구분되어야 한다.
7. 동시성 테스트에서 결과가 일관되어야 한다.

## 4. 아키텍처 개요

### 패키지 구조

```text
com.ilway.coupon
├─ common
│  ├─ api
│  └─ exception
├─ coupon
│  ├─ event
│  │  └─ api
│  └─ issue
│     └─ api
└─ comparison
   └─ unsafe
```

### 계층 구조

- Controller: HTTP 요청/응답 처리
- Service: 트랜잭션 경계와 비즈니스 규칙 처리
- Repository: JPA + 락 쿼리 처리
- Entity: 도메인 상태와 핵심 규칙 보유

### 요청 흐름

1. `POST /api/coupon-events/{id}/issues` 요청 수신
2. `CouponIssueService.issue()` 진입
3. `coupon_event`를 `PESSIMISTIC_WRITE`로 조회
4. 발급 기간 검증
5. 중복 발급 여부 조회
6. `issuedQuantity` 증가
7. `coupon_issue` 저장
8. 커밋 시 락 해제

## 5. DB 스키마 설명

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
  updated_at datetime(6) not null
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

### 왜 이 제약조건이 중요한가

- `not null`: 수량/시간 컬럼의 무결성을 강제한다.
- `unique(coupon_event_id, user_id)`: 같은 유저가 같은 이벤트에서 2번 발급되는 것을 DB가 최종 차단한다.
- `coupon_event_id` 인덱스: 이벤트별 발급 건수 조회를 빠르게 만든다.
- `user_id` 인덱스: 내 발급 이력 조회를 빠르게 만든다.

## 6. API

### 이벤트 생성

- `POST /api/coupon-events`

```json
{
  "name": "오픈 기념 이벤트",
  "totalQuantity": 100,
  "startAt": "2026-03-26T20:00:00",
  "endAt": "2026-03-26T21:00:00"
}
```

### 쿠폰 발급

- `POST /api/coupon-events/{couponEventId}/issues`

```json
{
  "userId": 1
}
```

### 이벤트 조회

- `GET /api/coupon-events/{couponEventId}`

### 내 발급 이력 조회

- `GET /api/users/{userId}/coupon-issues`

### 관리자 통계 조회

- `GET /api/admin/coupon-events/{couponEventId}/statistics`

현재 1차 버전의 관리자 통계는 `성공 건수/발급 수량/잔여 수량` 중심이다.  
실패 건수와 실패 사유별 집계는 `요청 이력 테이블`이 필요하므로 2차 확장으로 남겼다.

## 7. 동시성 문제 설명

### 왜 `@Transactional`만으로는 부족한가

`@Transactional`은 "여러 쿼리를 하나의 트랜잭션으로 묶는 기능"이지, 자동으로 모든 요청을 직렬화해 주는 기능이 아니다.

예를 들어 아래 흐름은 unsafe 하다.

1. 트랜잭션 A가 이벤트 조회
2. 트랜잭션 B도 같은 이벤트 조회
3. 둘 다 `issuedQuantity < totalQuantity`라고 판단
4. 둘 다 발급 성공 처리

즉, `조회 -> 검증 -> 수정` 사이에 다른 트랜잭션이 끼어들 수 있다.

### 일반 조회와 `SELECT ... FOR UPDATE` 차이

- 일반 조회: 다른 트랜잭션도 같은 행을 읽고 수정 판단을 할 수 있다.
- `SELECT ... FOR UPDATE`: 해당 행을 수정하려는 다른 트랜잭션이 대기한다.

이 프로젝트의 safe 구현은 `CouponEventRepository.findByIdForUpdate()`에서 이 락을 건다.

### 비관적 락은 언제 걸리고 언제 풀리는가

`CouponIssueService.issue()`에서 아래 쿼리가 실행되는 순간 락이 걸린다.

- `findByIdForUpdate(couponEventId)`

그리고 락은 아래 시점까지 유지된다.

- 트랜잭션 `commit`
- 또는 `rollback`

즉, 락 구간은 대략 다음과 같다.

1. `SELECT ... FOR UPDATE`
2. 기간 검증
3. 중복 발급 검증
4. 수량 증가
5. 발급 이력 저장
6. 커밋
7. 락 해제

### unique index가 왜 중요한가

애플리케이션에서 먼저 `existsBy...`로 중복 발급을 검사해도, 그 검사는 여전히 코드 레벨 검사다.

DB unique index는 마지막 안전장치다.

- 코드가 잘못되더라도
- 다른 경로에서 insert가 들어오더라도
- 경쟁 상황에서 두 요청이 거의 동시에 들어오더라도

최종적으로 DB가 중복 row 생성을 막아준다.

### "수량 차감"과 "중복 발급 방지"는 왜 다른 문제인가

- 수량 차감: `coupon_event.issued_quantity`라는 공유 숫자를 안전하게 증가시키는 문제
- 중복 발급 방지: `(coupon_event_id, user_id)` 조합이 중복되지 않게 보장하는 문제

하나의 락이나 하나의 if 문으로 두 문제를 동시에 해결했다고 생각하면 설계가 흔들리기 쉽다.

이 프로젝트는 두 문제를 분리했다.

- 수량: 비관적 락
- 중복: unique index + 선행 조회

## 8. unsafe 구현 vs safe 구현

### A. unsafe 비교용 구현

위치:

- `comparison.unsafe.UnsafeCouponIssueService`

특징:

- 일반 `findById()` 조회
- `exists` 체크 후 저장
- 별도 unique 제약 없음
- 비교 테스트를 위해 race window를 넓히는 짧은 지연 포함

이 구현은 운영용이 아니다.

학습 포인트:

- `@Transactional`만 있어도 oversell이 생길 수 있다.
- 같은 유저의 동시 요청에서 중복 발급이 생길 수 있다.

### B. safe 최종 구현

위치:

- `coupon.issue.CouponIssueService`

전략:

1. `coupon_event`를 `PESSIMISTIC_WRITE`로 잠근다.
2. 잠금 상태에서 발급 기간을 확인한다.
3. 잠금 상태에서 중복 발급 여부를 확인한다.
4. `issuedQuantity`를 증가시킨다.
5. `coupon_issue` 저장 시 unique index로 최종 방어한다.

왜 이 전략을 택했는가:

- 학습용으로 가장 이해하기 쉽다.
- 락이 언제 걸리는지 설명하기 쉽다.
- `SELECT ... FOR UPDATE`를 직접 보여줄 수 있다.
- MySQL 단일 DB 환경에서 단순하고 안전하다.

## 9. 조건부 UPDATE 방식과 비관적 락 방식의 차이

### 조건부 UPDATE 방식

예시:

```sql
update coupon_event
set issued_quantity = issued_quantity + 1
where id = ?
  and issued_quantity < total_quantity;
```

장점:

- 쿼리 한 번으로 수량 제어 가능
- 락 범위가 상대적으로 짧을 수 있음

단점:

- 중복 발급 문제를 따로 해결해야 함
- 읽기 흐름이 덜 직관적이라 학습 초기에 이해가 어렵다
- 기간 검증/상태 검증/실패 사유 분기가 늘어나면 코드 설명이 복잡해진다

### 비관적 락 방식

장점:

- `조회 -> 검증 -> 변경` 흐름이 자연스럽다
- 락 구간이 명확하다
- 학습용으로 설명하기 가장 쉽다

단점:

- 같은 이벤트에 대한 동시 요청이 많으면 직렬화가 심해진다
- 이벤트 단위로 병목이 생길 수 있다

### 왜 1차 버전은 비관적 락을 선택했는가

이번 1차의 목표는 "가장 고성능"이 아니라 "왜 안전한지 명확히 설명 가능한 구조"다.  
그래서 이 프로젝트에서는 비관적 락이 가장 적절하다.

## 10. 낙관적 락을 선택하지 않은 이유

낙관적 락은 충돌 시 재시도가 필요하고, 학습 초기에 보면 "실패 후 다시 시도"까지 같이 이해해야 한다.

이번 주제는 먼저 아래를 명확히 배우는 데 있다.

- 동시 조회가 왜 위험한지
- DB 락이 어떻게 직렬화를 만드는지
- unique index가 어떤 보호막인지

그래서 1차 버전에서는 낙관적 락보다 비관적 락이 학습 효율이 높다.

## 11. requestId / idempotencyKey를 1차에 넣지 않은 이유

이번 1차에서는 넣지 않았다.

이유:

- 핵심 학습 주제가 DB 동시성 제어이기 때문이다.
- idempotencyKey를 넣으면 요청 이력 저장, TTL 정책, 중복 재시도 처리까지 함께 설계해야 한다.
- 이건 2차 확장 주제로 분리하는 것이 학습 흐름상 좋다.

즉, 현재 중복 발급 방지는 "사용자 기준 중복"만 해결하고, "같은 API 요청의 멱등성"은 다음 단계로 남겼다.

## 12. 테스트 시나리오

### 단위 테스트

- 이벤트 기간 판정
- 발급 가능 여부
- 수량 계산
- 재고 소진 시 예외

### 통합 테스트

- 이벤트 생성 성공
- 정상 발급
- 중복 발급 실패
- 수량 소진 후 실패
- 발급 이력 조회
- 관리자 통계 조회

### 동시성 테스트

- safe: 수량 100개, 동시 요청 1000개 -> 성공 100 / 실패 900
- safe: 동일 userId 100번 동시 요청 -> 성공 1 / 실패 99
- safe: 여러 userId 동시 요청 -> 총 발급량 초과 없음
- unsafe: oversell 발생 재현
- unsafe: same user 중복 발급 재현

## 13. 현재 검증 결과

`2026-03-26` 기준 로컬에서 아래 명령을 실행했다.

```bash
./gradlew test
```

결과:

- 단위 테스트 4개 실행, 모두 성공
- MySQL/Testcontainers 기반 통합 테스트 6개는 Docker 데몬이 꺼져 있어 skip
- MySQL/Testcontainers 기반 동시성 테스트 5개는 Docker 데몬이 꺼져 있어 skip

즉, 테스트 코드는 모두 작성되어 있고, Docker Desktop만 실행하면 같은 명령으로 MySQL 기준 검증까지 가능하다.

## 14. 실행 방법

### 애플리케이션 실행

로컬 MySQL을 준비한 뒤 아래 환경변수를 맞춘다.

```bash
DB_URL=jdbc:mysql://localhost:3306/coupon?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=root
```

실행:

```bash
./gradlew bootRun
```

### 테스트 실행

단위 테스트만 확인:

```bash
./gradlew test
```

통합 테스트와 동시성 테스트까지 실제 실행하려면 Docker Desktop을 켜서 Testcontainers가 MySQL 컨테이너를 띄울 수 있어야 한다.

## 15. 향후 확장 방향

### 2차에서 바로 해볼 수 있는 것

- `requestId` 또는 `idempotencyKey` 도입
- 요청 이력 테이블 추가 후 실패 건수 / 실패 사유 집계
- 조건부 UPDATE 방식 버전 추가
- 낙관적 락 실험 버전 추가

### 실무 확장 방향

- Redis 분산 락 도입
- 대기열/선착순 큐 도입
- 비동기 이벤트 처리
- 쿠폰 발급 요청 수집 후 백그라운드 처리
- 이벤트 오픈 시점 트래픽 완충을 위한 rate limit

실무에서는 단일 DB 락만으로 버티기 어렵다.  
하지만 그 전에 "DB 한 대에서 무엇이 안전하고 무엇이 unsafe 한지"를 이해하는 것이 먼저다.

## 16. 포트폴리오 한 줄 요약

`Spring Boot + MySQL 기반 선착순 쿠폰 발급 시스템을 구현하며, 비관적 락과 unique index를 이용해 초과 발급과 중복 발급을 방지하고 동시성 테스트로 검증한 프로젝트`

## 17. 예상 면접 질문 5개

### 1. `@Transactional`만으로 왜 동시성 문제가 해결되지 않나요?

핵심 답변 포인트:

- 트랜잭션은 원자성/일관성 경계를 주지만 자동 직렬화를 보장하지 않는다.
- `조회 -> 검증 -> 수정` 사이에 다른 트랜잭션이 끼어들 수 있다.
- 그래서 별도의 락 또는 원자적 갱신 쿼리가 필요하다.

### 2. 일반 조회와 `SELECT ... FOR UPDATE`는 무엇이 다른가요?

핵심 답변 포인트:

- 일반 조회는 읽기만 하고 잠그지 않는다.
- `FOR UPDATE`는 해당 행에 배타 락을 걸어 다른 수정 트랜잭션을 대기시킨다.
- 이 프로젝트에서는 이벤트 행을 직렬화해서 oversell을 막았다.

### 3. 락은 언제부터 언제까지 유지되나요?

핵심 답변 포인트:

- `findByIdForUpdate()` 쿼리가 실행되는 순간부터 락이 걸린다.
- 같은 트랜잭션 안에서 검증/수정/저장을 마친다.
- 커밋 또는 롤백 시점에 락이 풀린다.

### 4. 왜 unique index가 필요한가요?

핵심 답변 포인트:

- 서비스 코드의 중복 체크는 보조 수단이다.
- 최종적으로 DB가 중복 row를 허용하지 않아야 안전하다.
- `(coupon_event_id, user_id)` unique가 중복 발급을 마지막으로 막는다.

### 5. 왜 조건부 UPDATE 대신 비관적 락을 선택했나요?

핵심 답변 포인트:

- 1차 목표가 최고 성능이 아니라 학습 가능한 안전성 확보였다.
- 비관적 락은 락 시점과 해제 시점을 설명하기 쉽다.
- 조건부 UPDATE는 더 고성능일 수 있지만 기간 검증/실패 분기/중복 처리 설명이 복잡해진다.
