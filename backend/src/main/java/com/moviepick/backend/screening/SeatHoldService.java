package com.moviepick.backend.screening;

import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.screening.dto.SeatDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 예매 화면/마이페이지 좌석변경 다이얼로그에서 사용자가 좌석을 "선택"하는 순간(결제/변경 확정 전)
// 서버에 임시로 선점(HOLD)을 걸어두는 서비스입니다. 다른 사용자 화면에는 이 좌석이 바로 "선택 중"으로
// 보이고 클릭도 막혀서, 결제 직전에야 "이미 예약된 좌석입니다" 충돌이 나던 문제를 원천 차단합니다
// (2026-10-14 요청). 좌석 행은 BookingService.create/changeSeat와 같은 PESSIMISTIC_WRITE 잠금
// (SeatRepository.findByIdForUpdate)을 그대로 재사용해 경합을 막습니다.
@Service
public class SeatHoldService {

    public static final int HOLD_TTL_MINUTES = 5;

    private final SeatRepository seatRepository;

    public SeatHoldService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @Transactional
    public SeatDto hold(Long screeningId, Long seatId, Long userId) {
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new ApiException("좌석을 찾을 수 없습니다: " + seatId, HttpStatus.NOT_FOUND));
        if (!seat.getScreeningId().equals(screeningId)) {
            throw new ApiException("다른 상영의 좌석입니다.", HttpStatus.BAD_REQUEST);
        }
        // BOOKED는 지연만료로도 절대 뒤집히지 않는 종결 상태라, isHeldByOther보다 먼저 확인합니다.
        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new ApiException("이미 예약된 좌석입니다: " + seat.getRowLabel() + seat.getColNo(), HttpStatus.CONFLICT);
        }
        if (seat.isHeldByOther(userId)) {
            throw new ApiException("다른 사용자가 선택 중인 좌석입니다: " + seat.getRowLabel() + seat.getColNo(), HttpStatus.CONFLICT);
        }
        // 이미 내가 선점 중이면(재클릭/폴링 갱신) 그대로 TTL만 연장합니다.
        seat.markHeld(userId, LocalDateTime.now().plusMinutes(HOLD_TTL_MINUTES));
        seatRepository.save(seat);
        return SeatDto.from(seat, userId, false);
    }

    // 좌석 선택을 해제하거나(디셀렉트), 화면을 벗어날 때(best-effort) 호출합니다. 내가 선점한 좌석이
    // 맞을 때만 해제하고, 그 외엔(좌석이 없거나/남의 좌석이거나/이미 해제·만료됐거나) 조용히 넘어가는
    // idempotent 동작입니다 - 프론트가 결과를 신경 쓰지 않고 fire-and-forget으로 호출하기 때문입니다.
    @Transactional
    public void release(Long screeningId, Long seatId, Long userId) {
        seatRepository.findByIdForUpdate(seatId).ifPresent(seat -> {
            if (seat.getScreeningId().equals(screeningId) && seat.isHeldBy(userId)) {
                seat.markAvailable();
                seatRepository.save(seat);
            }
        });
    }
}
