package com.moviepick.backend.wishlist;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.wishlist.dto.WishlistDto;
import com.moviepick.backend.wishlist.dto.WishlistRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;

    public WishlistService(WishlistRepository wishlistRepository, UserRepository userRepository) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
    }

    public List<WishlistDto> list(Long userId) {
        return wishlistRepository.findByUserIdOrderByAddedAtDesc(userId).stream()
                .map(WishlistDto::from)
                .toList();
    }

    public boolean isWishlisted(Long userId, String movieId) {
        return wishlistRepository.existsByUserIdAndMovieId(userId, movieId);
    }

    // 이미 찜한 영화를 다시 요청해도 에러 없이 그대로 둡니다(downloads/watched와 동일한 멱등 패턴).
    public WishlistDto add(Long userId, WishlistRequest request) {
        if (wishlistRepository.existsByUserIdAndMovieId(userId, request.movieId())) {
            return list(userId).stream()
                    .filter(dto -> dto.movieId().equals(request.movieId()))
                    .findFirst()
                    .orElseThrow();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
        Wishlist wishlist = new Wishlist(user, request.movieId(), request.movieTitle(), request.posterUrl());
        return WishlistDto.from(wishlistRepository.save(wishlist));
    }

    // 파생 삭제 쿼리는 자체 트랜잭션을 열지 않으므로 호출부에 명시적으로 @Transactional이 필요합니다.
    @Transactional
    public void remove(Long userId, String movieId) {
        wishlistRepository.deleteByUserIdAndMovieId(userId, movieId);
    }
}
