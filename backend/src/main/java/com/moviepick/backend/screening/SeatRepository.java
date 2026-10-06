package com.moviepick.backend.screening;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByScreeningIdOrderByRowLabelAscColNoAsc(Long screeningId);

    // 예매 생성/좌석변경 모두 "좌석이 비어있는지 확인 -> BOOKED로 바꾸기"를 한 트랜잭션 안에서 하는데,
    // 그냥 findById로 읽으면 두 사용자가 동시에 같은 좌석을 예매/변경 중일 때 둘 다 AVAILABLE을 보고
    // 둘 다 성공해버리는 경합(race condition)이 생깁니다. PESSIMISTIC_WRITE로 그 좌석 행을 커밋 때까지
    // 잠가서, 나중 트랜잭션은 먼저 트랜잭션이 끝날 때까지 기다렸다가 바뀐 상태(BOOKED)를 보고 정상적으로
    // "이미 예약된 좌석입니다"로 거절되도록 합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id in :ids")
    List<Seat> findAllByIdForUpdate(@Param("ids") List<Long> ids);
}
