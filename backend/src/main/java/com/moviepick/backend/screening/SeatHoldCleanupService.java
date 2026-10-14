package com.moviepick.backend.screening;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 좌석 선점(HOLD)의 정확성은 Seat.effectiveStatus()의 지연 만료 판정으로 이미 보장되므로, 이 배치는
// DB에 남는 stale HELD 행을 주기적으로 AVAILABLE로 되돌리는 위생 목적일 뿐입니다(관리자 화면/직접 DB
// 조회 시 실제로는 만료된 선점이 계속 HELD로 보이는 걸 막아줍니다). TTL이 5분뿐이라 매주/매시간 단위인
// ScreeningGenerationService의 배치 주기로는 너무 늦게 치워지므로 1분 간격으로 돕니다.
@Slf4j
@Service
public class SeatHoldCleanupService {

    private final SeatRepository seatRepository;

    public SeatHoldCleanupService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @Scheduled(fixedRate = 60 * 1000)
    @Transactional
    public void releaseExpiredHolds() {
        int released = seatRepository.releaseExpiredHolds(LocalDateTime.now());
        if (released > 0) {
            log.info("만료된 좌석 선점 {}건을 AVAILABLE로 되돌렸습니다.", released);
        }
    }
}
