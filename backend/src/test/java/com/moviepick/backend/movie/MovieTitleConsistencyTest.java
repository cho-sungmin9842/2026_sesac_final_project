package com.moviepick.backend.movie;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.review.Review;
import com.moviepick.backend.review.ReviewRepository;
import com.moviepick.backend.review.ReviewService;
import com.moviepick.backend.review.dto.ReviewDto;
import com.moviepick.backend.review.dto.ReviewRequest;
import com.moviepick.backend.wishlist.Wishlist;
import com.moviepick.backend.wishlist.WishlistRepository;
import com.moviepick.backend.wishlist.WishlistService;
import com.moviepick.backend.wishlist.dto.WishlistDto;
import com.moviepick.backend.wishlist.dto.WishlistRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 리뷰/찜/시청완료가 자기 테이블에 복사해 둔 영화 제목이 아니라 movies 테이블의 제목으로 보이고 저장되는지 확인합니다
// (2026-10-19: 리뷰에 "지금 우리 학교 는"처럼 옛 정제 규칙의 제목이 남아 다른 화면과 달라 보이던 문제).
class MovieTitleConsistencyTest {

    private final MovieRepository movieRepository = mock(MovieRepository.class);
    private final MovieTitleResolver resolver = new MovieTitleResolver(movieRepository);

    private Movie movie(String id, String title) {
        Movie movie = mock(Movie.class);
        when(movie.getMovieId()).thenReturn(id);
        when(movie.getTitle()).thenReturn(title);
        return movie;
    }

    @Test
    void movies에_있으면_그_제목_없으면_저장된_제목을_쓴다() {
        Movie schoolMovie = movie("K_1", "지금 우리 학교는");
        when(movieRepository.findById("K_1")).thenReturn(Optional.of(schoolMovie));
        when(movieRepository.findById("K_2")).thenReturn(Optional.empty());

        assertThat(resolver.titleFor("K_1", "지금 우리 학교 는")).isEqualTo("지금 우리 학교는");
        assertThat(resolver.titleFor("K_2", "요청에 담긴 제목")).isEqualTo("요청에 담긴 제목");
    }

    @Test
    void 리뷰_목록은_저장된_제목이_틀려도_movies_제목으로_내려준다() {
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(9L);
        Review stale = mock(Review.class);
        when(stale.getUser()).thenReturn(author);
        when(stale.getMovieId()).thenReturn("K_1");
        when(stale.getMovieTitle()).thenReturn("지금 우리 학교 는");
        when(reviewRepository.findByUserIdOrderByCreatedAtDescIdDesc(9L)).thenReturn(List.of(stale));
        Movie schoolMovie = movie("K_1", "지금 우리 학교는");
        when(movieRepository.findAllById(any())).thenReturn(List.of(schoolMovie));

        ReviewService service = new ReviewService(reviewRepository, mock(UserRepository.class), resolver);
        List<ReviewDto> result = service.listByUser(9L);

        assertThat(result).extracting(ReviewDto::movieTitle).containsExactly("지금 우리 학교는");
    }

    @Test
    void 리뷰를_저장할_때_프론트가_보낸_제목_대신_movies_제목을_저장한다() {
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(reviewRepository.existsByUserIdAndMovieId(9L, "K_1")).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Movie schoolMovie = movie("K_1", "지금 우리 학교는");
        when(movieRepository.findById("K_1")).thenReturn(Optional.of(schoolMovie));

        ReviewService service = new ReviewService(reviewRepository, userRepository, resolver);
        service.create("K_1", 9L, new ReviewRequest("지금 우리 학교 는", 5, "재밌어요"));

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getMovieTitle()).isEqualTo("지금 우리 학교는");
    }

    @Test
    void 찜을_저장할_때도_movies_제목을_저장한다() {
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = mock(User.class);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(wishlistRepository.existsByUserIdAndMovieId(9L, "K_1")).thenReturn(false);
        when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Movie schoolMovie = movie("K_1", "지금 우리 학교는");
        when(movieRepository.findById("K_1")).thenReturn(Optional.of(schoolMovie));

        WishlistService service = new WishlistService(wishlistRepository, userRepository, resolver);
        WishlistDto dto = service.add(9L, new WishlistRequest("K_1", "지금 우리 학교 는", "http://poster"));

        assertThat(dto.movieTitle()).isEqualTo("지금 우리 학교는");
    }
}
