package com.moviepick.backend.screening;

import com.moviepick.backend.booking.BookingRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.screening.dto.ScreeningDto;
import com.moviepick.backend.screening.dto.SeatDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class ScreeningController {

    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;

    public ScreeningController(
            ScreeningRepository screeningRepository, SeatRepository seatRepository, BookingRepository bookingRepository
    ) {
        this.screeningRepository = screeningRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
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
     * 예매 화면/마이페이지 좌석 배치도 다이얼로그가 공통으로 쓰는 좌석 현황. 실제 seats 테이블 상태
     * (AVAILABLE/BOOKED)를 그대로 내려주므로, 예매가 생성될 때마다 여기 응답도 바로 반영됩니다.
     * X-User-Id를 보내면(마이페이지 쪽만 보냄 - 예매 화면의 좌석 선택은 그 구분이 필요 없습니다) 각
     * 좌석이 "내가 예매한 좌석"인지(bookedByMe)도 함께 내려줘, 다른 예매자의 좌석과 구분해 보여줄 수
     * 있습니다.
     */
    @GetMapping("/api/screenings/{id}/seats")
    public List<SeatDto> getSeats(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long viewerUserId
    ) {
        if (!screeningRepository.existsById(id)) {
            throw new ApiException("상영정보를 찾을 수 없습니다: " + id, HttpStatus.NOT_FOUND);
        }
        Map<Long, Long> seatOwnerUserId = viewerUserId == null ? Map.of() : seatOwnerUserId(id);
        return seatRepository.findByScreeningIdOrderByRowLabelAscColNoAsc(id).stream()
                .map(seat -> SeatDto.from(seat, viewerUserId != null && viewerUserId.equals(seatOwnerUserId.get(seat.getId()))))
                .toList();
    }

    // 이 상영의 예매들을 모아 "좌석 id -> 예매한 사용자 id" 맵을 만듭니다.
    private Map<Long, Long> seatOwnerUserId(Long screeningId) {
        return bookingRepository.findByScreeningId(screeningId).stream()
                .flatMap(booking -> booking.getSeats().stream()
                        .map(bookingSeat -> Map.entry(bookingSeat.getSeatId(), booking.getUser().getId())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
