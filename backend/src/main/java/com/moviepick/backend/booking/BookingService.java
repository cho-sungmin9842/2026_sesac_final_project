package com.moviepick.backend.booking;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.booking.dto.BookingDto;
import com.moviepick.backend.booking.dto.BookingRequest;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.Movie;
import com.moviepick.backend.movie.MovieRepository;
import com.moviepick.backend.notification.NotificationService;
import com.moviepick.backend.screening.Screening;
import com.moviepick.backend.screening.ScreeningRepository;
import com.moviepick.backend.screening.Seat;
import com.moviepick.backend.screening.SeatRepository;
import com.moviepick.backend.screening.SeatStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookingService {

    // 상영 시작 이 시간(분) 전까지만 예매할 수 있습니다.
    private static final int BOOKING_CUTOFF_MINUTES = 30;

    // 요금 계산 시 인원 구분을 순서대로 훑으면서 요청에 담긴 seatIds를 앞에서부터 그 수만큼 배정합니다
    // (화면에는 인원 구분별 합계만 있고 "이 좌석은 성인용"처럼 좌석별 지정이 없으므로, 어떤 좌석이 어느
    // 구분이든 요금에는 영향이 없습니다 - 같은 구분이면 좌석 위치와 무관하게 요금이 같기 때문입니다).
    private static final List<String> CATEGORY_ORDER = List.of("adult", "teen", "child", "senior");
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "adult", "성인", "teen", "청소년", "child", "어린이", "senior", "우대"
    );

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final NotificationService notificationService;

    public BookingService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository,
            MovieRepository movieRepository,
            NotificationService notificationService
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.screeningRepository = screeningRepository;
        this.seatRepository = seatRepository;
        this.movieRepository = movieRepository;
        this.notificationService = notificationService;
    }

    // 좌석 예약 생성과 좌석 상태 갱신이 한 트랜잭션 안에서 함께 성공/실패해야 하고, Screening의 theater는
    // 지연 로딩(open-in-view: false)이라 같은 트랜잭션 안에서 읽어야 합니다.
    @Transactional
    public BookingDto create(Long userId, BookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));

        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new ApiException("상영정보를 찾을 수 없습니다: " + request.screeningId(), HttpStatus.NOT_FOUND));
        Movie movie = movieRepository.findById(screening.getMovieId()).orElse(null);

        validateBookingCutoff(screening);
        validateAgeRating(movie, request.ticketCounts());

        List<Seat> seats = seatRepository.findAllById(request.seatIds());
        if (seats.size() != new LinkedHashSet<>(request.seatIds()).size()) {
            throw new ApiException("존재하지 않는 좌석이 포함되어 있습니다.", HttpStatus.BAD_REQUEST);
        }

        Set<String> alreadyBooked = new LinkedHashSet<>();
        for (Seat seat : seats) {
            if (!seat.getScreeningId().equals(request.screeningId())) {
                throw new ApiException("다른 상영의 좌석이 포함되어 있습니다.", HttpStatus.BAD_REQUEST);
            }
            if (seat.getStatus() == SeatStatus.BOOKED) {
                alreadyBooked.add(seat.getRowLabel() + seat.getColNo());
            }
        }
        if (!alreadyBooked.isEmpty()) {
            throw new ApiException("이미 예약된 좌석입니다: " + String.join(", ", alreadyBooked), HttpStatus.CONFLICT);
        }

        List<BookingSeat> bookingSeats = assignCategories(request.seatIds(), request.ticketCounts(), seats.size());
        int totalPrice = calculateTotalPrice(request.ticketCounts(), screening);

        Booking booking = bookingRepository.save(new Booking(user, screening.getId(), bookingSeats, totalPrice));

        // 더 이상 무작위 시뮬레이션이 아니라, 실제 예매 결과로 좌석 상태를 갱신합니다.
        seats.forEach(Seat::markBooked);
        seatRepository.saveAll(seats);

        BookingDto dto = toDto(booking, screening, seats, movie);
        notificationService.notifyBookingCompleted(user, booking.getId(), bookingSummary(dto));
        return dto;
    }

    // 마이페이지 "예매 내역" 탭. 영화/상영관 정보는 bookings에 중복 저장돼 있지 않으므로, 예매에 걸린
    // screening/movie/seat를 한 번에 모아 조회해서 N+1 없이 조립합니다. Screening.theater는 지연 로딩이라
    // 트랜잭션 안에서 읽어야 합니다.
    @Transactional(readOnly = true)
    public List<BookingDto> listByUser(Long userId) {
        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (bookings.isEmpty()) {
            return List.of();
        }

        List<Long> screeningIds = bookings.stream().map(Booking::getScreeningId).distinct().toList();
        Map<Long, Screening> screeningsById = screeningRepository.findAllById(screeningIds).stream()
                .collect(Collectors.toMap(Screening::getId, s -> s));

        List<String> movieIds = screeningsById.values().stream().map(Screening::getMovieId).distinct().toList();
        Map<String, Movie> moviesById = movieRepository.findAllById(movieIds).stream()
                .collect(Collectors.toMap(Movie::getMovieId, m -> m));

        List<Long> seatIds = bookings.stream()
                .flatMap(booking -> booking.getSeats().stream().map(BookingSeat::getSeatId))
                .distinct().toList();
        Map<Long, Seat> seatsById = seatRepository.findAllById(seatIds).stream()
                .collect(Collectors.toMap(Seat::getId, s -> s));

        return bookings.stream()
                .map(booking -> toListDto(booking, screeningsById.get(booking.getScreeningId()), moviesById, seatsById))
                .toList();
    }

    private BookingDto toListDto(Booking booking, Screening screening, Map<String, Movie> moviesById, Map<Long, Seat> seatsById) {
        Movie movie = screening == null ? null : moviesById.get(screening.getMovieId());
        List<String> seatLabels = booking.getSeats().stream()
                .map(bookingSeat -> seatsById.get(bookingSeat.getSeatId()))
                .filter(Objects::nonNull)
                .map(seat -> seat.getRowLabel() + seat.getColNo())
                .toList();

        return new BookingDto(
                booking.getId(),
                screening != null ? screening.getMovieId() : null,
                movie != null ? movie.getTitle() : (screening != null ? screening.getMovieId() : "알 수 없음"),
                screening != null ? screening.getTheater().getName() : "-",
                screening != null ? screening.getDate() : null,
                screening != null ? screening.getStartTime().toString() : "-",
                seatLabels,
                booking.getTotalPrice(),
                booking.getCreatedAt()
        );
    }

    // 알림 문구에 쓰는 "영화명 상영관 날짜 시간 · 좌석 N석" 요약.
    private String bookingSummary(BookingDto dto) {
        return "\"" + dto.movieTitle() + "\" " + dto.theaterName() + " " + dto.showDate() + " " + dto.showtime()
                + " · 좌석 " + String.join(", ", dto.seats());
    }

    // 상영 시작 30분 전이 지났으면(이미 시작했거나 임박한 회차) 예매를 막습니다.
    private void validateBookingCutoff(Screening screening) {
        LocalDateTime showDateTime = LocalDateTime.of(screening.getDate(), screening.getStartTime());
        if (showDateTime.isBefore(LocalDateTime.now().plusMinutes(BOOKING_CUTOFF_MINUTES))) {
            throw new ApiException("상영 시작 " + BOOKING_CUTOFF_MINUTES + "분 전까지만 예매할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    // 영화 관람가 등급에 맞지 않는 인원 구분(예: 12세이상관람가 영화에 어린이)을 선택했는지 검사합니다.
    // 프론트도 선택 UI 자체를 막아두지만, 최종 판정은 서버가 해야 클라이언트 조작을 막을 수 있습니다.
    private void validateAgeRating(Movie movie, Map<String, Integer> ticketCounts) {
        Set<String> disallowed = AgeRatingPolicy.disallowedCategories(movie == null ? null : movie.getAgeRating());
        if (disallowed.isEmpty()) {
            return;
        }
        for (String category : disallowed) {
            Integer count = ticketCounts.get(category);
            if (count != null && count > 0) {
                String ageRating = movie == null ? "" : movie.getAgeRating();
                throw new ApiException(
                        ageRating + " 영화는 '" + CATEGORY_LABELS.getOrDefault(category, category) + "' 인원으로 예매할 수 없습니다.",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
    }

    // 요청받은 좌석 id 순서대로, 인원 구분 순서(성인→청소년→어린이→우대)대로 그 수만큼씩 배정합니다.
    private List<BookingSeat> assignCategories(List<Long> seatIds, Map<String, Integer> ticketCounts, int seatCount) {
        List<BookingSeat> result = new ArrayList<>();
        int index = 0;
        int totalTickets = 0;

        for (String category : CATEGORY_ORDER) {
            Integer count = ticketCounts.get(category);
            if (count == null) {
                continue;
            }
            if (count < 0) {
                throw new ApiException("인원 수는 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
            }
            totalTickets += count;
            for (int i = 0; i < count; i++) {
                result.add(new BookingSeat(seatIds.get(index), category));
                index++;
            }
        }

        for (String category : ticketCounts.keySet()) {
            if (!CATEGORY_ORDER.contains(category) && ticketCounts.get(category) > 0) {
                throw new ApiException("알 수 없는 인원 구분입니다: " + category, HttpStatus.BAD_REQUEST);
            }
        }

        if (totalTickets != seatCount) {
            throw new ApiException("선택한 인원 수와 좌석 수가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        return result;
    }

    // 요금은 클라이언트가 보낸 가격이 아니라, 연령 구분별 인원 수 × 상영 날짜/시간 기준(시간대·주중/주말·공휴일)
    // 요금표로 서버가 직접 계산합니다.
    private int calculateTotalPrice(Map<String, Integer> ticketCounts, Screening screening) {
        int totalPrice = 0;
        for (Map.Entry<String, Integer> entry : ticketCounts.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            totalPrice += count * TicketPricing.priceFor(entry.getKey(), screening.getDate(), screening.getStartTime().toString());
        }
        return totalPrice;
    }

    private BookingDto toDto(Booking booking, Screening screening, List<Seat> seats, Movie movie) {
        Map<Long, Seat> seatsById = seats.stream().collect(java.util.stream.Collectors.toMap(Seat::getId, s -> s));
        List<String> seatLabels = booking.getSeats().stream()
                .map(bookingSeat -> seatsById.get(bookingSeat.getSeatId()))
                .map(seat -> seat.getRowLabel() + seat.getColNo())
                .toList();
        String movieTitle = movie != null ? movie.getTitle() : screening.getMovieId();

        return new BookingDto(
                booking.getId(),
                screening.getMovieId(),
                movieTitle,
                screening.getTheater().getName(),
                screening.getDate(),
                screening.getStartTime().toString(),
                seatLabels,
                booking.getTotalPrice(),
                booking.getCreatedAt()
        );
    }
}
