package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.PreferredGenresDto;
import com.moviepick.backend.auth.dto.UserAdminDto;
import com.moviepick.backend.booking.BookingRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.review.ReviewRepository;
import com.moviepick.backend.wishlist.WishlistRepository;
import org.springframework.http.HttpStatus;
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

    public UserService(
            UserRepository userRepository,
            ReviewRepository reviewRepository,
            BookingRepository bookingRepository,
            WishlistRepository wishlistRepository
    ) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.wishlistRepository = wishlistRepository;
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
