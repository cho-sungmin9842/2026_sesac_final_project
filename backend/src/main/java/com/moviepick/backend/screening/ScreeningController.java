package com.moviepick.backend.screening;

import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.screening.dto.ScreeningDto;
import com.moviepick.backend.screening.dto.SeatDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ScreeningController {

    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;

    public ScreeningController(ScreeningRepository screeningRepository, SeatRepository seatRepository) {
        this.screeningRepository = screeningRepository;
        this.seatRepository = seatRepository;
    }

    /**
     * 예매 화면의 상영관/날짜/회차 선택지. movieId는 목록/상세와 같은 합성 id("{movieId}_{movieSeq}")이며,
     * 매주 월요일 자정 배치(ScreeningGenerationService)가 생성해둔 이번 주 상영정보 중 이 영화 것만 내려줍니다.
     */
    @GetMapping("/api/screenings")
    public List<ScreeningDto> getScreenings(@RequestParam String movieId) {
        return screeningRepository.findByMovieIdOrderByDateAscStartTimeAsc(movieId).stream()
                .map(ScreeningDto::from)
                .toList();
    }

    /**
     * 예매 화면의 좌석 배치도. 실제 seats 테이블 상태(AVAILABLE/BOOKED)를 그대로 내려주므로,
     * 예매가 생성될 때마다 여기 응답도 바로 반영됩니다.
     */
    @GetMapping("/api/screenings/{id}/seats")
    public List<SeatDto> getSeats(@PathVariable Long id) {
        if (!screeningRepository.existsById(id)) {
            throw new ApiException("상영정보를 찾을 수 없습니다: " + id, HttpStatus.NOT_FOUND);
        }
        return seatRepository.findByScreeningIdOrderByRowLabelAscColNoAsc(id).stream()
                .map(SeatDto::from)
                .toList();
    }
}
