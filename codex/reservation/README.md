# Seat Reservation Toy Project - 1차

## 1. 프로젝트 개요
- 기존 Spring Boot + MySQL 학습 프로젝트 골격 위에 좌석 예매 도메인을 확장했다.
- 목표는 "결제 직전까지의 좌석 HOLD / 확정 / 취소 / 만료 / 멱등성"을 안전하게 설명하는 것이다.
- 기술 스택은 Spring Boot, Spring Web MVC, Spring Data JPA, MySQL, JUnit, Testcontainers만 사용했다.

## 2. 왜 쿠폰 다음 단계로 좌석 예매를 선택했는가
- 쿠폰은 "남은 수량"을 줄이는 문제라서 한 row의 재고 관리에 초점이 맞는다.
- 좌석 예매는 "A-10 같은 개별 자원"을 누가 먼저 잡느냐의 문제다.
- 그래서 같은 동시성 제어를 써도 병목의 모양이 달라진다.
- 쿠폰은 event row가 hot row가 되기 쉽고, 좌석 예매는 같은 seatId끼리만 직접 경쟁한다.

## 3. 쿠폰 발급과 좌석 예매의 공통점 / 차이점
### 공통점
- 동시에 들어오는 요청 중 하나만 성공시켜야 한다.
- 네트워크 재시도는 멱등하게 처리해야 한다.
- 실패 사유를 명확히 남겨야 나중에 분석할 수 있다.
- 통합 테스트보다 동시성 테스트가 더 중요하다.

### 차이점
- 쿠폰은 "남은 수량 1개"를 누가 가져가느냐이고, 좌석은 "A-10"이라는 개별 자원을 누가 선점하느냐이다.
- 쿠폰은 재고 차감이 핵심이지만, 좌석은 `HOLD -> RESERVED` 상태 전이가 핵심이다.
- 쿠폰은 보통 event row가 병목이 되지만, 좌석은 seat row가 병목이 된다.
- 좌석은 HOLD 만료가 반드시 들어가야 하고, 쿠폰은 보통 즉시 성공/실패로 끝난다.

## 4. 핵심 비즈니스 규칙
- 존재하지 않는 공연/좌석은 예매할 수 없다.
- 예매 오픈 전 / 마감 후에는 HOLD할 수 없다.
- 이미 HOLD 또는 RESERVED 상태인 좌석은 다른 사용자가 가져갈 수 없다.
- HOLD는 TTL이 있고, 만료되면 EXPIRED로 바뀌고 좌석은 다시 AVAILABLE로 간주된다.
- RESERVED 좌석은 다시 HOLD할 수 없다.
- 같은 `userId + action(HOLD) + Idempotency-Key` 요청은 이전 결과를 재사용한다.
- 같은 사용자가 같은 좌석을 다른 키로 다시 잡는 것은 "재시도"가 아니라 "새 요청"이다.

## 5. 도메인 모델 설명
### `Show`
- 공연 정보와 예매 가능 시간대를 가진다.

### `Seat`
- 실제로 경쟁이 발생하는 개별 자원이다.
- 좌석 번호(`A1`, `A2`...)와 `showId`를 가진다.
- `(showId, seatNumber)` unique 제약으로 공연 내 좌석 번호 중복을 막는다.

### `SeatReservation`
- 좌석 HOLD/RESERVED/CANCELLED/EXPIRED 상태와 이력을 관리한다.
- `holdExpiresAt`, `confirmedAt`, `cancelledAt`, `expiredAt`를 기록한다.
- 이번 1차에서는 좌석의 현재 상태를 `Seat`에 중복 저장하지 않고, `SeatReservation`이 상태의 소스 오브 트루스가 된다.

### `SeatReservationRequest`
- 쿠폰 2차의 request history 패턴을 그대로 가져온 엔티티다.
- `idempotencyKey`, `showId`, `seatId`, `userId`, `requestStatus`, `failureReason`, `reservationId`, `reusedCount`를 가진다.
- 역할:
  - 같은 HTTP 재시도 판별
  - 성공/실패 결과 재사용
  - 실패 사유 집계
  - 멱등 재사용 횟수 집계

## 6. 상태 전이 설명
- `AVAILABLE`
  - 예약 row가 없거나, 최신 예약이 `CANCELLED` 또는 `EXPIRED`인 상태
- `HOLD`
  - 좌석을 잠깐 선점한 상태
- `RESERVED`
  - HOLD 유효 시간 안에 확정된 상태
- `CANCELLED`
  - 1차에서는 HOLD 취소만 지원한다
- `EXPIRED`
  - HOLD 시간이 지나 자동 만료된 상태

상태 전이가 필요한 이유는 결제 전 단계를 표현해야 하기 때문이다.
좌석 예매는 "바로 성공/실패"가 아니라 "잠깐 잡아두고, 그 안에 확정하거나 놓치는" 흐름이다.

## 7. 좌석 HOLD 전략 설명
- 기본 전략은 `Seat` row에 대한 비관적 락(`PESSIMISTIC_WRITE`)이다.
- 락 대상은 `seatId` 하나다.
- HOLD 요청 흐름:
  1. 공연 존재 / 예매 시간 검증
  2. `Seat` row lock 획득
  3. 최신 `SeatReservation` 조회
  4. 만료된 HOLD면 `EXPIRED`로 전이
  5. 현재 상태가 `HOLD`면 `SEAT_ALREADY_HELD`
  6. 현재 상태가 `RESERVED`면 `SEAT_ALREADY_RESERVED`
  7. 새 `SeatReservation(HOLD)` 생성

이 구조가 안전한 이유:
- 같은 좌석은 같은 row를 잠그므로 직렬화된다.
- 다른 좌석은 서로 다른 row를 잠그므로 병렬 처리된다.
- 쿠폰처럼 event 전체가 hot row가 되는 구조보다 병렬성이 좋다.

## 8. 락이 언제 걸리고 언제 풀리는지
- 락은 `SeatReservationTxService.processHold/confirm/cancel/expireReservationIfNeeded`에서 `Seat` row를 읽을 때 걸린다.
- 락은 해당 트랜잭션이 커밋되거나 롤백될 때 풀린다.
- 같은 좌석 경쟁만 막고, 다른 좌석은 락 범위가 겹치지 않는다.

중요한 구현 포인트:
- 이번 프로젝트에서는 MySQL 기본 격리 수준(REPEATABLE_READ) 때문에 snapshot read 문제가 생길 수 있다.
- 그래서 HOLD/CONFIRM/CANCEL/EXPIRE 트랜잭션은 `READ_COMMITTED`로 두었다.
- 이유는 seat lock을 잡은 뒤 최신 예약 상태를 반드시 보게 하기 위해서다.

## 9. HOLD 만료 전략 설명
- 기본 안전장치는 lazy expiration이다.
- 즉, HOLD나 CONFIRM 시점에 `holdExpiresAt`을 다시 확인하고, 이미 만료됐으면 `EXPIRED`로 바꾼다.
- 여기에 `@Scheduled` 정리 작업을 추가했다.

왜 별도 로직이 필요한가:
- HOLD는 시간이 지나면 자동으로 효력이 사라져야 한다.
- 만료 처리가 없으면 좌석이 영원히 잠긴다.

왜 이 조합을 선택했는가:
- lazy expiration이 correctness를 보장한다.
- scheduler는 조회/통계 일관성을 정리하는 보조 역할만 맡는다.
- 1차 학습 단계에서 가장 단순하면서도 설명하기 쉽다.

## 10. 멱등성 / 요청 이력 설명
- HOLD API는 `Idempotency-Key`를 받는다.
- 같은 `userId + HOLD + Idempotency-Key` 조합은 같은 HTTP 재시도로 본다.
- 처리 규칙:
  - 이전 성공이면 같은 reservation 결과를 재사용
  - 이전 실패면 같은 실패 사유를 재사용
  - 아직 진행 중이면 `DUPLICATE_REQUEST_IN_PROGRESS`
  - 같은 키를 다른 `showId/seatId/userId`에 재사용하면 `IDEMPOTENCY_PAYLOAD_MISMATCH`

쿠폰 프로젝트와 연결되는 지점:
- request history 테이블
- 멱등 키 등록
- 실패 사유 enum 분류
- 재사용 횟수 집계

## 11. API 목록
### 공연
- `POST /api/shows`

### 좌석
- `POST /api/seats`
- `GET /api/seats/shows/{showId}`

### 예약
- `POST /api/reservations/hold`
  - Header: `Idempotency-Key`
- `POST /api/reservations/{reservationId}/confirm`
- `POST /api/reservations/{reservationId}/cancel`
- `GET /api/reservations?userId={userId}`

### 관리자
- `GET /api/admin/statistics/shows/{showId}`

## 12. 테스트 시나리오
### 단위 테스트
- 예매 가능 시간 판정
- HOLD 만료 판정
- 상태 전이 가능 여부
- 요청 이력 payload 매칭 / 성공 / 실패 / 재사용 카운트

### 통합 테스트
- 공연 생성
- 좌석 등록
- 좌석 목록 조회
- 정상 HOLD
- 같은 키 재요청 결과 재사용
- 만료 전 확정 성공
- 만료 후 확정 실패
- HOLD 취소 성공
- 내 예약 조회
- 관리자 통계 조회

### 동시성 테스트
- 같은 seatId에 100개 동시 HOLD 요청 시 정확히 1개만 성공
- 서로 다른 seatId에는 동시에 HOLD 가능
- 같은 userId + 같은 idempotencyKey 재요청 시 결과 재사용
- HOLD 만료 직전/직후 경계 테스트
- 이미 RESERVED된 좌석에 대한 동시 요청 실패
- 요청 이력 기반 실패 사유 집계 검증

## 13. 이번 1차의 한계
- 실제 결제 연동이 없다.
- HOLD 성공 응답 원본 자체를 request history에 snapshot으로 저장하지는 않았다.
- RESERVED 취소 정책은 아직 단순화했다.
- 다중 좌석 동시 선택은 다루지 않았다.
- 조건부 UPDATE / 낙관적 락 비교 버전은 아직 넣지 않았다.

## 14. 향후 2차 확장 방향
- 다중 좌석 연속 선택과 락 획득 순서 설계
- 결제 연동 전용 상태 추가
  - `PAYMENT_PENDING`, `PAYMENT_FAILED`, `RESERVATION_CONFIRMED`
- 만료 배치 고도화
- 조건부 UPDATE 버전과 비관적 락 버전 비교
- 낙관적 락 버전 비교
- 좌석 홀드 대기열 / 큐
- 결제용 idempotency 분리

## 15. 쿠폰 프로젝트에서 가져온 개념 / 새로 배운 개념
### 그대로 가져온 것
- 비관적 락
- Idempotency-Key
- request history
- 실패 사유 집계
- 동시성 테스트

### 새로 배운 것
- 좌석은 개별 자원이라서 락 단위가 event가 아니라 seat가 된다는 점
- 결제 전 HOLD 상태가 필요하다는 점
- 상태 전이와 만료 처리가 동시성만큼 중요하다는 점
- 같은 좌석 경쟁과 다른 좌석 병렬 처리를 동시에 설명해야 한다는 점

## 16. 면접에서 말할 수 있는 포인트
- "쿠폰은 수량 경쟁이고, 좌석은 개별 자원 경쟁이라 락 단위가 다릅니다."
- "좌석 예매 1차에서는 seat row 비관적 락으로 같은 좌석만 직렬화했습니다."
- "정합성은 lazy expiration이 보장하고, scheduler는 cleanup 역할만 맡겼습니다."
- "멱등성은 seat occupancy와 별개 문제라 request history로 분리했습니다."
- "MySQL REPEATABLE_READ에서 snapshot read 문제를 피하기 위해 HOLD 트랜잭션은 READ_COMMITTED로 조정했습니다."

## 17. 실행 / 테스트
```bash
./gradlew test
```

Windows:
```powershell
.\gradlew.bat test
```
