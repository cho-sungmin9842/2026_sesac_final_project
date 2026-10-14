package com.moviepick.backend.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

// 상영 1건당 좌석 하나(8행 A~H x 14열, A열은 전부 휠체어석). 상영정보 생성 배치가 상영정보와 함께 만듭니다.
@Entity
@Table(name = "seats")
@Getter
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screening_id", nullable = false)
    private Long screeningId;

    @Column(name = "row_label", nullable = false, length = 1)
    private String rowLabel;

    @Column(name = "col_no", nullable = false)
    private int colNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status;

    // 좌석을 선택(클릭)한 시점에 임시로 선점한 사용자와 그 선점의 만료 시각입니다 - status가 HELD일
    // 때만 의미가 있고, 그 외엔 null입니다.
    @Column(name = "held_by_user_id")
    private Long heldByUserId;

    @Column(name = "held_until")
    private LocalDateTime heldUntil;

    protected Seat() {
    }

    public Seat(Long screeningId, String rowLabel, int colNo, SeatType seatType, SeatStatus status) {
        this.screeningId = screeningId;
        this.rowLabel = rowLabel;
        this.colNo = colNo;
        this.seatType = seatType;
        this.status = status;
    }

    // 예매가 실제로 확정됐을 때 좌석 상태를 BOOKED로 바꿉니다(서버가 호출 전에 이미 AVAILABLE인지 확인합니다).
    // 선점 정보도 함께 지워서, 예매된 좌석에 과거 선점 흔적이 남지 않게 합니다.
    public void markBooked() {
        this.status = SeatStatus.BOOKED;
        this.heldByUserId = null;
        this.heldUntil = null;
    }

    // 좌석변경/선점 해제로 비워진 좌석을 다시 AVAILABLE로 되돌립니다.
    public void markAvailable() {
        this.status = SeatStatus.AVAILABLE;
        this.heldByUserId = null;
        this.heldUntil = null;
    }

    // 좌석을 선택(클릭)한 시점에 TTL과 함께 임시로 선점합니다.
    public void markHeld(Long userId, LocalDateTime until) {
        this.status = SeatStatus.HELD;
        this.heldByUserId = userId;
        this.heldUntil = until;
    }

    // 저장된 status가 HELD라도 held_until이 지났으면 지연 만료로 간주해 AVAILABLE처럼 취급합니다.
    // 아래 모든 헬퍼와 BookingService/SeatHoldService의 가용성 판단이 전부 이 메서드 하나를 거칩니다 -
    // 별도의 만료 처리 배치가 늦어지거나 실패해도 정확성에는 영향이 없습니다.
    public SeatStatus effectiveStatus() {
        if (status == SeatStatus.HELD && (heldUntil == null || heldUntil.isBefore(LocalDateTime.now()))) {
            return SeatStatus.AVAILABLE;
        }
        return status;
    }

    public boolean isHeldBy(Long userId) {
        return effectiveStatus() == SeatStatus.HELD && Objects.equals(heldByUserId, userId);
    }

    public boolean isHeldByOther(Long userId) {
        return effectiveStatus() == SeatStatus.HELD && !isHeldBy(userId);
    }

    // 예매 생성/좌석변경이 쓰는 단 하나의 가용성 판정: 진짜 AVAILABLE이거나, 지연만료로 사실상
    // AVAILABLE이거나, 내가 이미 선점 중인 좌석이면 예매할 수 있습니다.
    public boolean isAvailableFor(Long userId) {
        return effectiveStatus() == SeatStatus.AVAILABLE || isHeldBy(userId);
    }
}
