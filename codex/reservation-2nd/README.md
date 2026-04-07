# Seat Reservation Toy Project - 2차

## 1. 한 줄 요약
다중 좌석 원자적 HOLD, seat lock ordering, `ReservationGroup` aggregate, 결제 직전 상태 전이, 그룹 단위 idempotency를 붙인 2차 좌석 예매 프로젝트다.

## 2. 왜 2차가 더 실무적인가
1차는 "좌석 1개를 안전하게 HOLD한다"에 집중했다.
이 단계는 동시성 입문으로는 좋지만, 실무에서 더 어려운 문제는 보통 "여러 좌석을 한 번에 잡되 일부만 성공하면 안 되는 상황"이다.

2차에서 난이도가 올라가는 이유는 다음과 같다.
- 단일 좌석 선점은 row 1개만 잠그면 되지만, 다중 좌석 선점은 여러 row를 함께 잠가야 한다.
- 여러 row를 서로 다른 순서로 잠그면 데드락 위험이 커진다.
- 한 요청 안에서 3석 중 2석만 HOLD되는 부분 성공을 허용하면 결제 UX와 운영 정합성이 바로 깨진다.
- 단건 `SeatReservation`만으로는 "같은 결제/주문 단위로 묶인 좌석들"을 설명하기 어렵다.
- 멱등성도 seat 1개 기준이 아니라 "같은 공연 + 같은 사용자 + 같은 좌석 조합" 기준으로 올라와야 한다.

즉 1차가 "같은 좌석 충돌을 막는 법"이었다면, 2차는 "여러 자원을 한 번에 잠그면서도 전체 성공/전체 실패를 보장하는 법"이다.

## 3. 현재 구조 분석 요약
1차 `codex/reservation`에서 재사용한 핵심은 아래 3가지다.
- `Seat` row 비관적 락으로 실제 경쟁 자원을 직렬화한다.
- `request history + Idempotency-Key`로 HTTP 재시도를 비즈니스 처리와 분리한다.
- lazy expiration + scheduler cleanup 조합으로 만료 정합성을 유지한다.

쿠폰 프로젝트에서 가져온 패턴은 `request history`, 실패 사유 enum, `REQUIRES_NEW` 기반 결과 기록이다.
다만 쿠폰은 보통 event row가 hot row가 되지만, 좌석 예매 2차는 여러 `seat` row를 함께 잠그는 문제가 새로 등장한다.

## 4. 2차 도메인 모델
### `ReservationGroup`
- 하나의 예약 주문 단위다.
- 필드:
  - `showId`
  - `userId`
  - `status`
  - `holdExpiresAt`
  - `paymentPendingAt`
  - `reservedAt`
  - `cancelledAt`
  - `expiredAt`

왜 필요한가:
- 다중 좌석은 "좌석 3개"가 아니라 "한 번의 주문"으로 다뤄야 한다.
- 확정, 취소, 만료, 조회, 통계를 seat 단건이 아니라 그룹 단위로 설명할 수 있다.
- 부분 성공 금지 정책을 aggregate 단위로 강제할 수 있다.

### `ReservationGroupSeat`
- 그룹에 속한 좌석 목록이다.
- 필드:
  - `reservationGroupId`
  - `seatId`
  - `seatNumber`
  - `status`

역할:
- 어떤 좌석이 어떤 그룹에 묶였는지 남긴다.
- 좌석 목록 조회에서 "현재 어떤 그룹이 좌석을 점유 중인지" 계산할 수 있다.

### `ReservationGroupRequest`
- 그룹 HOLD 요청 이력이다.
- 필드:
  - `idempotencyKey`
  - `showId`
  - `userId`
  - `normalizedSeatSelectionKey`
  - `requestStatus`
  - `failureReason`
  - `reservationGroupId`
  - `reusedCount`

역할:
- 같은 논리 요청 재시도 판별
- 성공/실패 결과 재사용
- 진행 중 중복 요청 대기 후 replay
- 실패 사유 집계와 멱등 재사용 횟수 집계

## 5. 좌석 선택 정규화 정책
입력 좌석 순서는 논리적으로 의미 없다고 판단했다.

정책:
- `[11,12,13]`과 `[13,12,11]`은 같은 좌석 조합으로 본다.
- 내부에서는 항상 오름차순으로 정렬해 `normalizedSeatSelectionKey`를 만든다.
- 같은 요청 안의 중복 입력 `[11,11,12]`는 `INVALID_SEAT_SELECTION`으로 거절한다.

이 정책을 택한 이유:
- 사용자가 UI에서 누른 순서는 재시도 식별 기준으로 의미가 없다.
- lock ordering도 같은 정렬 결과를 재사용하므로, 멱등성과 데드락 완화가 같은 기준으로 맞춰진다.

## 6. 상태 전이
### 그룹 상태
- `HOLD`
- `PAYMENT_PENDING`
- `RESERVED`
- `CANCELLED`
- `EXPIRED`

### 현재 구현의 의미
- 외부 PG 호출은 아직 없다.
- 하지만 실무에서는 `HOLD -> PAYMENT_PENDING -> RESERVED` 경계가 중요하다.
- 그래서 confirm 내부에서 `PAYMENT_PENDING` 단계를 명시적으로 거친다.

왜 `PAYMENT_PENDING`을 넣었는가:
- 실제 결제를 붙이면 DB 트랜잭션 안에 PG 호출을 오래 물고 있을 수 없다.
- 따라서 "좌석은 잡혀 있지만 아직 최종 확정은 아님"을 표현할 상태가 필요하다.
- 이번 2차에서는 외부 결제가 없어서 최종적으로는 `RESERVED`로 끝나지만, 상태 머신에는 그 경계를 남겼다.

### 취소 정책
- 이번 단계에서는 `HOLD` 상태 취소만 지원한다.
- 이미 `RESERVED`가 된 그룹의 환불/복구 정책은 결제, 정산, 좌석 재오픈 정책까지 묶이므로 3차 이후로 미뤘다.

## 7. 락 전략
이번 2차의 핵심이다.

### 어떤 row를 잠그는가
- 실제 경쟁 자원은 여전히 `Seat` row다.
- HOLD, CONFIRM, CANCEL, EXPIRE는 모두 관련된 `seat` row들을 잠근다.
- 같은 그룹의 중복 상태 전이는 `ReservationGroup` row를 `PESSIMISTIC_WRITE`로 먼저 잠근다.

### 어떤 순서로 잠그는가
- 항상 `seatId` 오름차순이다.
- 구현 위치:
  - `SeatSelectionNormalizer`
  - `SeatRepository.findAllByShowIdAndIdInOrderByIdAscForUpdate`

### 락은 언제부터 언제까지 유지되는가
- `ReservationGroupTxService`의 각 트랜잭션에서 `SELECT ... FOR UPDATE` 시점부터 락이 걸린다.
- 트랜잭션이 커밋 또는 롤백될 때 풀린다.

### 왜 lock ordering이 중요한가
- 요청 A가 `[1,2,3]`, 요청 B가 `[3,2,1]`을 각각 입력 순서대로 잠그면 원형 대기가 생길 수 있다.
- 둘 다 오름차순 `[1,2,3]`으로 잠그면 한 쪽이 먼저 기다리더라도 circular wait 가능성이 크게 줄어든다.
- 1차에서는 seat row 1개만 잠궜기 때문에 이 문제가 거의 없었고, 2차에서 여러 row를 동시에 잡기 시작하면서 본격적으로 등장한다.

### 왜 부분 성공이 발생하지 않는가
- 한 트랜잭션 안에서 다음 순서로 처리한다.
  1. 공연/예매 시간 검증
  2. seat row 전체 lock 획득
  3. 최신 그룹 아이템 조회
  4. 만료 lazy expiration
  5. 점유 좌석 검증
  6. 그룹 생성
  7. 그룹 아이템 생성
- 검증이 모두 끝나기 전에는 그룹을 저장하지 않는다.
- 따라서 하나라도 막히면 전체 롤백되고 3석 중 2석만 HOLD되는 상태가 남지 않는다.

### 왜 서로 다른 좌석 세트는 병렬 처리 가능한가
- 락 범위가 seat row 단위라서 겹치지 않는 seat set은 서로 다른 row를 잠근다.
- `[1,2]`와 `[3,4]`는 병렬 처리된다.
- `[1,2,3]`와 `[3,4,5]`는 seat 3에서만 충돌한다.

### 쿠폰의 hot row 문제와 왜 다른가
- 쿠폰은 보통 event/coupon row 하나가 병목이 된다.
- 좌석 예매는 같은 seat를 두고 경쟁하는 경우에만 직접 충돌한다.
- 대신 2차부터는 "여러 seat row를 한 번에 잠그는 순서"가 새로운 난제가 된다.

## 8. HOLD 만료 전략
- 기본 정합성 보장은 lazy expiration이다.
- HOLD/CONFIRM/CANCEL/좌석 조회 시점마다 만료 여부를 다시 확인한다.
- 추가로 scheduler가 만료된 그룹을 cleanup 한다.

선택 이유:
- scheduler가 잠깐 늦어도 정합성은 lazy expiration이 보장한다.
- 만료된 HOLD가 좌석을 영구 점유하는 문제를 막을 수 있다.

## 9. 그룹 단위 idempotency / request history
정책:
- 같은 `userId + HOLD + Idempotency-Key`는 같은 HTTP 재시도다.
- payload 검증 기준은 `showId + normalizedSeatSelectionKey`다.
- 이전 성공이면 같은 그룹 결과를 재사용한다.
- 이전 실패면 같은 실패를 재사용한다.
- 진행 중 중복 요청은 짧게 기다렸다가 완료 결과를 replay한다.
- 너무 오래 끝나지 않으면 `DUPLICATE_REQUEST_IN_PROGRESS`로 끊는다.

현재 구현의 한계:
- request history에 첫 응답 snapshot 전체를 저장하지는 않는다.
- `reservationGroupId`를 저장하고 현재 그룹 상태를 다시 조회해 응답을 만든다.
- 따라서 첫 HOLD 응답이 `HOLD`였더라도, 나중에 같은 key 재요청 시 이미 `RESERVED`로 바뀐 상태가 내려올 수 있다.

## 10. 실패 사유 분류
- `SHOW_NOT_FOUND`
- `SEAT_NOT_FOUND`
- `GROUP_NOT_FOUND`
- `INVALID_SEAT_SELECTION`
- `BOOKING_NOT_OPEN`
- `BOOKING_CLOSED`
- `SEAT_ALREADY_HELD`
- `SEAT_PAYMENT_PENDING`
- `SEAT_ALREADY_RESERVED`
- `HOLD_EXPIRED`
- `FORBIDDEN_GROUP_ACCESS`
- `DUPLICATE_REQUEST_IN_PROGRESS`
- `IDEMPOTENCY_PAYLOAD_MISMATCH`
- `INVALID_GROUP_STATE`
- `INTERNAL_ERROR`

분류 기준:
- malformed input은 `INVALID_SEAT_SELECTION`
- show 경로 기준으로 존재하지 않는 seat 조합은 `SEAT_NOT_FOUND`
- 점유 충돌은 `SEAT_ALREADY_HELD / SEAT_PAYMENT_PENDING / SEAT_ALREADY_RESERVED`
- 상태 전이 불가 문제는 `INVALID_GROUP_STATE`

## 11. API
### 공연
- `POST /api/shows`

### 좌석
- `POST /api/seats`
- `GET /api/seats/shows/{showId}`
  - 현재 좌석 상태
  - `reservationGroupId`
  - `holdExpiresAt`

### 그룹 HOLD
- `POST /api/shows/{showId}/reservation-groups/hold`
- Header: `Idempotency-Key`

request
```json
{
  "userId": 7,
  "seatIds": [11, 12, 13]
}
```

### 그룹 조회
- `GET /api/reservation-groups/{groupId}`

### 내 그룹 목록
- `GET /api/users/{userId}/reservation-groups`

### 그룹 확정
- `POST /api/reservation-groups/{groupId}/confirm`

### 그룹 취소
- `POST /api/reservation-groups/{groupId}/cancel`

### 관리자 통계
- `GET /api/admin/statistics/shows/{showId}`

응답 항목:
- 총 그룹 요청 수
- 성공 HOLD 그룹 수
- 실패 그룹 요청 수
- 상태별 그룹 수
- 실패 사유별 집계
- 멱등 재사용 횟수
- 좌석 충돌 실패 건수

## 12. 테스트
### 단위 테스트
- seat selection normalization
- 그룹 상태 전이 가능 여부
- hold 만료 판정
- request history payload/reuse 정책
- 실패 사유 분류

### 통합 테스트
- 다중 좌석 HOLD 성공
- 일부 좌석 충돌 시 전체 실패
- 같은 key 재요청 재사용
- 그룹 확정 성공
- 그룹 취소 성공
- 만료 후 확정 실패
- 관리자 통계 조회

### 동시성 테스트
- 같은 좌석 조합 동시 HOLD 시 정확히 1개만 성공
- 일부 겹치는 좌석 조합 `[1,2,3] vs [3,4,5]`
- 서로 다른 좌석 조합 병렬 처리
- 동일한 그룹 요청 + 같은 idempotencyKey 동시 재시도 결과 재사용
- lock ordering 설명 테스트
- 부분 성공이 남지 않는지 검증

## 13. 이번 2차에서 배운 점
- 단일 자원 락과 다중 자원 락은 완전히 다른 문제다.
- 실무에서는 "충돌을 막는 것"만큼 "부분 성공을 금지하는 것"이 중요하다.
- 멱등성은 HTTP 재시도 문제이고, seat occupancy는 자원 경쟁 문제라서 분리해서 다뤄야 한다.
- `PAYMENT_PENDING` 같은 중간 상태는 PG 연동이 없어도 설계에 미리 드러나야 한다.

## 14. 면접에서 말할 수 있는 포인트
- "1차는 seat 1개를 잡는 문제였고, 2차부터는 여러 seat row를 같은 순서로 잠그는 문제가 생겼습니다."
- "다중 좌석에서는 seatId 오름차순 lock ordering으로 deadlock risk를 낮췄습니다."
- "부분 성공을 막기 위해 seat row 전체 lock 이후에만 group과 group item을 생성했습니다."
- "쿠폰 프로젝트의 request history 패턴을 재사용했지만, 좌석 예매는 여러 자원을 함께 잠그는 문제가 추가됐습니다."
- "실제 결제가 없어도 `PAYMENT_PENDING` 경계를 상태 머신에 남겨서 다음 단계 확장을 준비했습니다."

## 15. 현재 한계
- 실제 PG 결제 연동은 없다.
- `PAYMENT_PENDING`이 외부 결제 대기 상태로 오래 유지되는 흐름은 아직 없다.
- HOLD 응답 snapshot replay까지는 구현하지 않았다.
- 예약 확정 후 환불/복구 정책은 단순화했다.
- Redis 분산 락, 대기열, 읽기 캐시, 좌석 추천은 범위 밖이다.

## 16. 3차 확장 방향
- 실제 결제 연동
- 결제 전용 idempotency 분리
- `PAYMENT_PENDING -> RESERVED / PAYMENT_FAILED` 비동기 전이
- HOLD 연장
- 연속 좌석 추천
- 대기열 / 분산 락
- 읽기 성능 최적화
- 조건부 UPDATE / 낙관적 락 비교 실험

## 17. 실행
```bash
./gradlew test
```

Windows:
```powershell
.\gradlew.bat test
```
