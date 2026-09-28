package com.moviepick.backend.booking;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.booking.dto.BookingDto;
import com.moviepick.backend.booking.dto.BookingRequest;
import com.moviepick.backend.booking.dto.ReservedSeatsDto;
import com.moviepick.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class BookingService {

    // 좌석 배치(한 줄당 좌석 수)는 화면 데모용 목업이라 프론트 bookingData.js의 SEATS_PER_ROW에만 있습니다 -
    // 여기 값도 반드시 맞춰야 합니다.
    private static final int SEATS_PER_ROW = 13;

    // 장애인(휠체어)석으로 판정할 좌석 코드 목록 - 맨 앞줄(A열) 전체. 프론트 bookingData.js의 ACCESSIBLE_SEATS와
    // 값을 맞춰야 합니다 - 최종 판정은 (클라이언트가 조작할 수 없도록) 여기 서버 쪽 목록 기준으로만 합니다.
    private static final Set<String> ACCESSIBLE_SEAT_CODES = IntStream.rangeClosed(1, SEATS_PER_ROW)
            .mapToObj(seatNumber -> "A" + seatNumber)
            .collect(Collectors.toUnmodifiableSet());

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository, UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    public ReservedSeatsDto getReservedSeats(String movieId, String theater, LocalDate showDate, String showtime) {
        Set<String> seats = new TreeSet<>();
        for (Booking booking : findBookings(movieId, theater, showDate, showtime)) {
            seats.addAll(booking.seatCodes());
        }
        return new ReservedSeatsDto(List.copyOf(seats));
    }

    public BookingDto create(Long userId, BookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));

        Set<String> alreadyReserved = new LinkedHashSet<>();
        for (Booking booking : findBookings(request.movieId(), request.theater(), request.showDate(), request.showtime())) {
            alreadyReserved.addAll(booking.seatCodes());
        }

        Set<String> requestedSeats = new LinkedHashSet<>(request.seats());
        Set<String> conflicting = new LinkedHashSet<>(requestedSeats);
        conflicting.retainAll(alreadyReserved);
        if (!conflicting.isEmpty()) {
            throw new ApiException("이미 예약된 좌석입니다: " + String.join(", ", conflicting), HttpStatus.CONFLICT);
        }

        int totalPrice = calculateTotalPrice(request.ticketCounts(), requestedSeats.size(), request.showDate());

        List<SeatSelection> seatSelections = requestedSeats.stream()
                .map(code -> new SeatSelection(code, classifySeatType(code)))
                .toList();

        Booking booking = new Booking(
                user,
                request.movieId(),
                request.movieTitle(),
                request.theater(),
                request.showDate(),
                request.showtime(),
                seatSelections,
                totalPrice
        );
        return BookingDto.from(bookingRepository.save(booking));
    }

    private SeatType classifySeatType(String seatCode) {
        return ACCESSIBLE_SEAT_CODES.contains(seatCode) ? SeatType.ACCESSIBLE : SeatType.REGULAR;
    }

    // 요금은 클라이언트가 보낸 가격이 아니라, 연령 구분별 인원 수 × showDate 기준(주중/주말·공휴일) 요금표로
    // 서버가 직접 계산합니다. 인원 수 합은 좌석 수와 반드시 같아야 합니다(자리마다 한 명씩).
    private int calculateTotalPrice(Map<String, Integer> ticketCounts, int seatCount, LocalDate showDate) {
        int totalTickets = 0;
        int totalPrice = 0;
        for (Map.Entry<String, Integer> entry : ticketCounts.entrySet()) {
            String category = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count < 0) {
                throw new ApiException("인원 수는 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
            }
            if (count > 0 && !TicketPricing.isKnownCategory(category)) {
                throw new ApiException("알 수 없는 인원 구분입니다: " + category, HttpStatus.BAD_REQUEST);
            }
            totalTickets += count;
            totalPrice += count * TicketPricing.priceFor(category, showDate);
        }
        if (totalTickets != seatCount) {
            throw new ApiException("선택한 인원 수와 좌석 수가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        return totalPrice;
    }

    private List<Booking> findBookings(String movieId, String theater, LocalDate showDate, String showtime) {
        return bookingRepository.findByMovieIdAndTheaterAndShowDateAndShowtime(movieId, theater, showDate, showtime);
    }
}
