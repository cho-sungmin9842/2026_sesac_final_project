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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// AuthService(회원가입/로그인)와 분리 - 이건 이미 로그인된 사용자의 프로필 설정을 다룹니다.
@Service
public class UserService {

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
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(user -> UserAdminDto.from(
                        user,
                        reviewRepository.countByUserId(user.getId()),
                        bookingRepository.countByUserId(user.getId()),
                        wishlistRepository.countByUserId(user.getId())
                ))
                .toList();
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
