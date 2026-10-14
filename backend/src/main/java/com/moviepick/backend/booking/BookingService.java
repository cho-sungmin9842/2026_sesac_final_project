package com.moviepick.backend.booking;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.booking.dto.BookingDto;
import com.moviepick.backend.booking.dto.BookingRequest;
import com.moviepick.backend.booking.dto.SeatChangeRequest;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.Movie;
import com.moviepick.backend.movie.MovieRepository;
import com.moviepick.backend.notification.NotificationService;
import com.moviepick.backend.screening.Screening;
import com.moviepick.backend.screening.ScreeningRepository;
import com.moviepick.backend.screening.Seat;
import com.moviepick.backend.screening.SeatRepository;
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
    // noRollbackFor: AI 채팅(ChatService.handleBookingRequest)이 이 메서드를 같은 트랜잭션 안에서 호출하고
    // ApiException을 직접 잡아 대화형 안내 문구로 바꿔 계속 진행하는데, 기본 설정대로면 이 메서드가
    // 참여(REQUIRED)하는 바깥 트랜잭션이 "rollback-only"로 표시되어 버려(이미 잡아서 처리했는데도) 이후
    // 커밋 시점에 UnexpectedRollbackException이 터지는 문제가 있었습니다(2026-10-11 실측 - 이미 예약된
    // 좌석을 다시 예매하면 500 에러). 이 메서드의 ApiException은 전부 실제 DB 변경(좌석/예매 저장) 전에만
    // 던져지므로(검증 실패), 롤백 대상으로 표시하지 않아도 데이터 정합성에 영향이 없습니다.
    @Transactional(noRollbackFor = ApiException.class)
    public BookingDto create(Long userId, BookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));

        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new ApiException("상영정보를 찾을 수 없습니다: " + request.screeningId(), HttpStatus.NOT_FOUND));
        Movie movie = movieRepository.findById(screening.getMovieId()).orElse(null);

        validateBookingCutoff(screening);
        validateAgeRating(movie, request.ticketCounts());

        // 두 사용자가 동시에 같은 좌석을 예매하지 못하도록, 이 트랜잭션이 끝날 때까지 좌석 행을 잠급니다.
        // 여러 좌석을 한 번에 잠글 때는 항상 같은 순서(id 오름차순)로 잠가야, 서로 다른 좌석 조합을
        // 동시에 예매하는 두 요청이 서로를 기다리며 교착(deadlock)되는 상황을 피할 수 있습니다.
        List<Long> sortedSeatIds = request.seatIds().stream().sorted().toList();
        List<Seat> seats = seatRepository.findAllByIdForUpdate(sortedSeatIds);
        if (seats.size() != new LinkedHashSet<>(request.seatIds()).size()) {
            throw new ApiException("존재하지 않는 좌석이 포함되어 있습니다.", HttpStatus.BAD_REQUEST);
        }

        // isAvailableFor: 진짜 AVAILABLE이거나, 지연만료로 사실상 AVAILABLE이거나, 내가 이미 선점
        // (HOLD)해둔 좌석이면 예매할 수 있습니다 - 결제 전 좌석 선택 단계에서 이미 SeatHoldService로
        // 선점해뒀을 테니, 정상 플로우라면 거의 항상 통과합니다(2026-10-14 좌석 선점 기능 추가).
        Set<String> unavailable = new LinkedHashSet<>();
        for (Seat seat : seats) {
            if (!seat.getScreeningId().equals(request.screeningId())) {
                throw new ApiException("다른 상영의 좌석이 포함되어 있습니다.", HttpStatus.BAD_REQUEST);
            }
            if (!seat.isAvailableFor(userId)) {
                unavailable.add(seat.getRowLabel() + seat.getColNo());
            }
        }
        if (!unavailable.isEmpty()) {
            throw new ApiException(
                    "이미 예약되었거나 다른 사용자가 선택 중인 좌석입니다: " + String.join(", ", unavailable), HttpStatus.CONFLICT);
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
                booking.getScreeningId(),
                screening != null ? screening.getMovieId() : null,
                movie != null ? movie.getTitle() : (screening != null ? screening.getMovieId() : "알 수 없음"),
                screening != null ? screening.getTheater().getName() : "-",
                screening != null ? screening.getDate() : null,
                screening != null ? screening.getStartTime().toString() : "-",
                seatLabels,
                booking.getTotalPrice(),
                booking.getCreatedAt(),
                ticketCounts(booking)
        );
    }

    // 마이페이지 예매 내역에 "성인 2 · 청소년 1"처럼 인원 구분별 인원 수를 보여주기 위해, 예매에 담긴
    // 좌석들을 ticketCategory별로 세어 돌려줍니다(0명인 구분은 결과에서 아예 빼서 화면에서 걸러낼
    // 필요 없게 합니다).
    private Map<String, Integer> ticketCounts(Booking booking) {
        return booking.getSeats().stream()
                .collect(Collectors.groupingBy(BookingSeat::getTicketCategory, Collectors.summingInt(seat -> 1)));
    }

    // 마이페이지 좌석 배치도 다이얼로그 - 내 예매에 포함된 좌석 하나를 아직 비어있는 다른 좌석으로
    // 바꿉니다. 인원 구분(성인/청소년/어린이/우대)과 요금은 그대로 유지됩니다(좌석 위치가 아니라
    // 인원 구분에 따라 결정되므로, 관람가 재검증도 필요 없습니다).
    // noRollbackFor: AI 채팅(ChatService)도 같은 트랜잭션 안에서 이 메서드를 호출하고 ApiException을
    // 직접 잡아 안내 문구로 바꿔 계속 진행하는데, 기본 설정대로면 참여(REQUIRED) 트랜잭션이
    // rollback-only로 표시돼버려 커밋 시점에 UnexpectedRollbackException이 나는 문제가 있었습니다
    // (create()에서 2026-10-11에 겪은 것과 같은 함정). 이 메서드의 ApiException도 전부 실제 DB 변경
    // 전에만 던져지므로 안전합니다.
    @Transactional(noRollbackFor = ApiException.class)
    public BookingDto changeSeat(Long userId, Long bookingId, SeatChangeRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException("예매 내역을 찾을 수 없습니다: " + bookingId, HttpStatus.NOT_FOUND));
        if (!booking.getUser().getId().equals(userId)) {
            throw new ApiException("본인의 예매만 좌석을 변경할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        Screening screening = screeningRepository.findById(booking.getScreeningId())
                .orElseThrow(() -> new ApiException("상영정보를 찾을 수 없습니다: " + booking.getScreeningId(), HttpStatus.NOT_FOUND));
        validateBookingCutoff(screening, "좌석을 변경");

        if (request.fromSeatId().equals(request.toSeatId())) {
            throw new ApiException("같은 좌석으로는 변경할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        // 두 사용자가 동시에 같은 좌석으로 변경하지 못하도록, 이 트랜잭션이 끝날 때까지 두 좌석 행을
        // 잠급니다. 항상 id가 작은 쪽부터 잠가야, 서로 반대 방향으로 좌석을 바꾸는 두 요청이 맞물려
        // 교착(deadlock)되는 상황을 피할 수 있습니다.
        Long smallerSeatId = Math.min(request.fromSeatId(), request.toSeatId());
        Long largerSeatId = Math.max(request.fromSeatId(), request.toSeatId());
        Seat smallerSeat = seatRepository.findByIdForUpdate(smallerSeatId)
                .orElseThrow(() -> new ApiException("좌석을 찾을 수 없습니다: " + smallerSeatId, HttpStatus.NOT_FOUND));
        Seat largerSeat = seatRepository.findByIdForUpdate(largerSeatId)
                .orElseThrow(() -> new ApiException("좌석을 찾을 수 없습니다: " + largerSeatId, HttpStatus.NOT_FOUND));
        Seat fromSeat = smallerSeatId.equals(request.fromSeatId()) ? smallerSeat : largerSeat;
        Seat toSeat = smallerSeatId.equals(request.toSeatId()) ? smallerSeat : largerSeat;

        if (!toSeat.getScreeningId().equals(booking.getScreeningId())) {
            throw new ApiException("다른 상영의 좌석으로는 변경할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (!toSeat.isAvailableFor(userId)) {
            throw new ApiException(
                    "이미 예약되었거나 다른 사용자가 선택 중인 좌석입니다: " + toSeat.getRowLabel() + toSeat.getColNo(), HttpStatus.CONFLICT);
        }

        if (!booking.replaceSeat(request.fromSeatId(), request.toSeatId())) {
            throw new ApiException("이 예매에 포함된 좌석이 아닙니다: " + request.fromSeatId(), HttpStatus.BAD_REQUEST);
        }

        fromSeat.markAvailable();
        toSeat.markBooked();
        seatRepository.saveAll(List.of(fromSeat, toSeat));
        bookingRepository.save(booking);

        Movie movie = movieRepository.findById(screening.getMovieId()).orElse(null);
        List<Seat> bookingSeats = seatRepository.findAllById(
                booking.getSeats().stream().map(BookingSeat::getSeatId).toList());
        BookingDto dto = toDto(booking, screening, bookingSeats, movie);
        notificationService.notifySeatChanged(booking.getUser(), booking.getId(), bookingSummary(dto));
        return dto;
    }

    // 마이페이지 "예매 내역" 탭의 "예매 취소" - 아직 상영이 시작되지 않은 예매만 취소할 수 있고, 취소하면
    // 걸려있던 좌석을 전부 다시 AVAILABLE로 되돌립니다(안 그러면 취소한 예매의 좌석이 영구히 BOOKED로
    // 남아 아무도 다시 예매할 수 없게 됩니다).
    // noRollbackFor: changeSeat()와 같은 이유로, AI 채팅이 이 메서드의 ApiException을 잡아 계속 진행해도
    // 바깥 트랜잭션이 rollback-only로 표시되지 않게 합니다(이 메서드의 ApiException도 전부 실제 DB
    // 변경 전에만 던져집니다).
    @Transactional(noRollbackFor = ApiException.class)
    public void cancel(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException("예매 내역을 찾을 수 없습니다: " + bookingId, HttpStatus.NOT_FOUND));
        if (!booking.getUser().getId().equals(userId)) {
            throw new ApiException("본인의 예매만 취소할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        Screening screening = screeningRepository.findById(booking.getScreeningId())
                .orElseThrow(() -> new ApiException("상영정보를 찾을 수 없습니다: " + booking.getScreeningId(), HttpStatus.NOT_FOUND));
        // 좌석변경과 같은 기준(상영 시작 30분 전까지만)으로 취소도 막습니다 - 임박한 회차를 막판에 취소하면
        // 그 좌석이 다시 풀려도 사실상 아무도 새로 예매할 시간이 없어, 예매 생성/좌석변경과 동일하게 다룹니다.
        validateBookingCutoff(screening, "취소");

        // 알림 문구에 쓸 요약은 예매가 지워지기 전에 미리 만들어둡니다.
        Movie movie = movieRepository.findById(screening.getMovieId()).orElse(null);
        List<Seat> bookingSeats = seatRepository.findAllById(
                booking.getSeats().stream().map(BookingSeat::getSeatId).toList());
        BookingDto dto = toDto(booking, screening, bookingSeats, movie);

        releaseSeats(booking);
        bookingRepository.delete(booking);
        notificationService.notifyBookingCancelled(booking.getUser(), bookingSummary(dto), dto.totalPrice());
    }

    // 회원 탈퇴 시 UserService가 사용자 행을 지우기 "전에" 호출합니다. 좌석만 풀어주고 예매 행을 세션에
    // 그대로 남겨둔 채 뒤이어 User를 지우면 Hibernate가 flush 시점에 "본인(User)을 참조하는 managed
    // Booking이 아직 남아있다"고 보고 TransientObjectException을 던집니다 - 그래서 cancel()과 똑같이
    // 좌석을 돌려놓은 뒤 예매 자체도 바로 지워서, User를 지울 때는 그 사용자의 Booking이 세션에 하나도
    // 남아있지 않게 합니다(탈퇴라 상영이 이미 끝났는지는 가리지 않습니다).
    @Transactional
    public void releaseAllSeatsForUser(Long userId) {
        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
        bookings.forEach(this::releaseSeats);
        bookingRepository.deleteAll(bookings);
    }

    private void releaseSeats(Booking booking) {
        List<Long> seatIds = booking.getSeats().stream().map(BookingSeat::getSeatId).toList();
        if (seatIds.isEmpty()) {
            return;
        }
        List<Seat> seats = seatRepository.findAllById(seatIds);
        seats.forEach(Seat::markAvailable);
        seatRepository.saveAll(seats);
    }

    // 알림 문구에 쓰는 "영화명 상영관 날짜 시간 · 좌석 N석" 요약.
    private String bookingSummary(BookingDto dto) {
        return "\"" + dto.movieTitle() + "\" " + dto.theaterName() + " " + dto.showDate() + " " + dto.showtime()
                + " · 좌석 " + String.join(", ", dto.seats());
    }

    // 상영 시작 30분 전이 지났으면(이미 시작했거나 임박한 회차) 예매를 막습니다.
    private void validateBookingCutoff(Screening screening) {
        validateBookingCutoff(screening, "예매");
    }

    // 예매 생성뿐 아니라 취소/좌석변경도 똑같이 상영 시작 30분 전까지만 가능합니다 - action은 에러 문구에
    // 쓰이는 동사("예매"/"취소"/"좌석을 변경")입니다.
    private void validateBookingCutoff(Screening screening, String action) {
        LocalDateTime showDateTime = LocalDateTime.of(screening.getDate(), screening.getStartTime());
        if (showDateTime.isBefore(LocalDateTime.now().plusMinutes(BOOKING_CUTOFF_MINUTES))) {
            throw new ApiException("상영 시작 " + BOOKING_CUTOFF_MINUTES + "분 전까지만 " + action + "할 수 있습니다.", HttpStatus.BAD_REQUEST);
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
                booking.getScreeningId(),
                screening.getMovieId(),
                movieTitle,
                screening.getTheater().getName(),
                screening.getDate(),
                screening.getStartTime().toString(),
                seatLabels,
                booking.getTotalPrice(),
                booking.getCreatedAt(),
                ticketCounts(booking)
        );
    }
}
