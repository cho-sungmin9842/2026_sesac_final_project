package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.PreferredGenresDto;
import com.moviepick.backend.auth.dto.UserAdminDto;
import com.moviepick.backend.booking.BookingRepository;
import com.moviepick.backend.booking.BookingService;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.review.ReviewRepository;
import com.moviepick.backend.wishlist.WishlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// AuthService(회원가입/로그인)와 분리 - 이건 이미 로그인된 사용자의 프로필 설정을 다룹니다.
@Service
public class UserService {

    // 관리자 "회원 관리" 화면의 온라인/오프라인 기준 - lastActiveAt이 이보다 더 최근이면 온라인입니다.
    private static final int ONLINE_THRESHOLD_MINUTES = 2;
    // touchActivity가 매 요청마다 DB에 쓰지 않도록 두는 간격 - 이보다 최근에 이미 갱신했으면 건너뜁니다.
    private static final int ACTIVITY_UPDATE_THROTTLE_SECONDS = 60;
    // 정지된 계정이 로그인을 시도하거나(AuthService.login), 이미 로그인된 상태에서 세션 유효성 재확인
    // (verifySessionActive)에 걸렸을 때 공통으로 쓰는 문구입니다 - 사유는 buildSuspendedMessage가 맨
    // 앞에 따로 붙이므로 이 문구 자체에는 포함하지 않습니다. 프론트(LoginPage.jsx)는 이 문구가 메시지에
    // 포함돼 있는지만 보고 다이얼로그를 띄우므로(사유가 앞에 붙어 위치가 바뀌어도 매칭되도록 includes로
    // 비교), 이 문구 자체는 바꾸지 않아야 합니다.
    public static final String SUSPENDED_MESSAGE_PREFIX = "이용이 정지된 사용자입니다.";

    // 정지된 계정에게 보여줄 안내 문구를 관리자가 고른 사유까지 포함해 만듭니다 - 사유가 맨 앞에 먼저
    // 보이고, 그 아래 기본 안내 문구가 이어집니다. 사유 없이 정지된(이론상 있을 수 없지만 방어적으로)
    // 계정은 사유 없이 기본 안내 문구만 보여줍니다.
    public static String buildSuspendedMessage(User user) {
        String base = SUSPENDED_MESSAGE_PREFIX + "\n관리자에게 문의하세요.";
        if (user.getSuspensionReason() == null) {
            return base;
        }
        String reasonLabel = SuspensionReason.fromCode(user.getSuspensionReason()).label();
        return "사유: " + reasonLabel + "\n" + base;
    }

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final WishlistRepository wishlistRepository;
    private final PasswordEncoder passwordEncoder;
    private final BookingService bookingService;

    public UserService(
            UserRepository userRepository,
            ReviewRepository reviewRepository,
            BookingRepository bookingRepository,
            WishlistRepository wishlistRepository,
            PasswordEncoder passwordEncoder,
            BookingService bookingService
    ) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.wishlistRepository = wishlistRepository;
        this.passwordEncoder = passwordEncoder;
        this.bookingService = bookingService;
    }

    // 마이페이지 "비밀번호 변경" - 비밀번호 찾기로 받은 임시 비밀번호(또는 기존 비밀번호)를 본인이 원하는
    // 비밀번호로 바꿉니다. 현재 비밀번호 확인을 거쳐야 하므로 아이디+닉네임만으로 바꿔주는
    // AuthService.resetPassword()와는 별도로 둡니다.
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException("현재 비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // 마이페이지 "회원 탈퇴" - 비밀번호 확인 후 계정을 완전히 삭제합니다. users 삭제는 리뷰/찜/시청완료/
    // 예매/알림/대화방 등을 전부 ON DELETE CASCADE로 함께 지우지만, 예매에 걸린 좌석(seats.status)은
    // cascade 대상이 아니라서 미리 전부 AVAILABLE로 되돌려놓아야 합니다(안 그러면 탈퇴한 사용자가
    // 예매했던 좌석이 영구히 BOOKED로 남습니다).
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException("비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        bookingService.releaseAllSeatsForUser(userId);
        userRepository.delete(user);
    }

    public PreferredGenresDto getPreferredGenres(Long userId) {
        return PreferredGenresDto.from(requireUser(userId));
    }

    @Transactional
    public PreferredGenresDto updatePreferredGenres(Long userId, List<String> genres) {
        User user = requireUser(userId);
        Set<String> cleaned = genres.stream()
                .map(String::trim)
                .filter(genre -> !genre.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        user.updatePreferredGenres(cleaned);
        return PreferredGenresDto.from(userRepository.save(user));
    }

    // 관리자 "회원 관리" 화면 - 전체 회원 목록(최근 가입자부터)을 활동 집계(리뷰/예매/찜 수)와 함께 보여줍니다.
    // 회원 수 자체가 많지 않은(데모용) 서비스라, 집계별로 group by 쿼리를 따로 만드는 대신 회원별로 그냥
    // 세 번씩 세는 간단한 방식을 씁니다.
    public List<UserAdminDto> listAllForAdmin(Long adminUserId) {
        requireAdmin(adminUserId);
        LocalDateTime onlineSince = LocalDateTime.now().minusMinutes(ONLINE_THRESHOLD_MINUTES);
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(user -> UserAdminDto.from(
                        user,
                        reviewRepository.countByUserId(user.getId()),
                        bookingRepository.countByUserId(user.getId()),
                        wishlistRepository.countByUserId(user.getId()),
                        user.getLastActiveAt() != null && user.getLastActiveAt().isAfter(onlineSince)
                ))
                .toList();
    }

    // UserActivityInterceptor가 인증된 요청(X-User-Id 헤더가 있는 모든 API 호출)마다 호출합니다.
    // userId가 올바른 회원이 아니어도(탈퇴/오타 등) 그냥 조용히 넘어갑니다 - 접속 상태 갱신은 부가
    // 기능이라 이 때문에 정상 요청을 실패시킬 이유가 없습니다.
    @Transactional
    public void touchActivity(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        userRepository.touchLastActiveIfStale(userId, now, now.minusSeconds(ACTIVITY_UPDATE_THROTTLE_SECONDS));
    }

    // 관리자 "회원 관리" 화면 - 정지/정지 해제를 토글합니다. 관리자 계정은 서로(또는 본인을) 정지시킬 수
    // 없게 막아둡니다 - 안 그러면 실수로 관리자 전원이 로그인 못 하게 잠기는 상황이 생길 수 있습니다.
    // 정지시킬 때(suspended=true)는 사유(reasonCode - SuspensionReason의 name())가 반드시 있어야 하고,
    // 해제할 때는 사유를 다시 비웁니다(다음에 다른 사유로 정지될 수 있으므로).
    @Transactional
    public void setSuspended(Long adminUserId, Long targetUserId, boolean suspended, String reasonCode) {
        requireAdmin(adminUserId);
        User target = requireUser(targetUserId);
        if (target.isAdmin()) {
            throw new ApiException("관리자 계정은 정지할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        target.setSuspended(suspended);
        target.setSuspensionReason(suspended ? SuspensionReason.fromCode(reasonCode).name() : null);
        userRepository.save(target);
        // 정지 즉시 "오프라인"으로 보이도록 접속 시각을 지웁니다(2분 기준이 자연히 지나길 기다리지
        // 않음). touchLastActiveIfStale의 WHERE 절이 정지된 계정은 아예 건너뛰므로, 그 사이 폴링 중이던
        // 요청이 와도 다시 "온라인"으로 되돌아가지 않습니다 - UserRepository 주석 참고.
        if (suspended) {
            userRepository.clearLastActive(targetUserId);
        }
    }

    // 관리자 "회원 관리" 화면 - 문제 회원을 관리자가 직접 삭제합니다. 본인 탈퇴(deleteAccount)와 달리
    // 비밀번호 확인 없이 바로 지워지므로, 좌석 해제 등 뒷정리는 동일하게 거칩니다. 관리자 계정은 이
    // 경로로 지울 수 없습니다(실수로 관리자를 전부 지워 아무도 관리 못 하게 되는 상황 방지).
    @Transactional
    public void deleteForAdmin(Long adminUserId, Long targetUserId) {
        requireAdmin(adminUserId);
        User target = requireUser(targetUserId);
        if (target.isAdmin()) {
            throw new ApiException("관리자 계정은 이 화면에서 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        bookingService.releaseAllSeatsForUser(targetUserId);
        userRepository.delete(target);
    }

    // 로그인된 화면이 주기적으로(AuthContext.jsx 폴링) 호출해 "지금도 여전히 유효한 세션인지"를
    // 확인합니다 - 로그인된 상태에서 관리자가 방금 정지시킨 경우, 다음 폴링에서 바로 강제 로그아웃
    // 시키기 위함입니다(정식 토큰 세션이 없는 데모 인증이라 서버가 먼저 끊을 방법이 없습니다).
    public void verifySessionActive(Long userId) {
        User user = requireUser(userId);
        if (user.isSuspended()) {
            throw new ApiException(buildSuspendedMessage(user), HttpStatus.FORBIDDEN);
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
    }

    private void requireAdmin(Long userId) {
        if (!requireUser(userId).isAdmin()) {
            throw new ApiException("관리자만 이용할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }
}
