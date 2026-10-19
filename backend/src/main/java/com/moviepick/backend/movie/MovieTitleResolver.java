package com.moviepick.backend.movie;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 영화 제목의 "기준값"을 movies 테이블에서 찾아주는 도우미입니다.
 * 리뷰/찜/시청완료는 영화 제목을 자기 테이블에 한 번 복사해 두는데, 그 값은 저장하던 시점에 프론트가 보낸
 * 문자열이라 제목 정제 규칙이 고쳐지기 전의 값("지금 우리 학교 는")이 그대로 남으면 영화 상세/목록/예매
 * 화면(= movies 기준)과 제목이 달라집니다. 그래서 저장할 때도, 보여줄 때도 이 도우미가 알려주는 movies의
 * 제목을 우선 쓰고, movies에 없는 영화일 때만 저장된 값을 씁니다.
 */
@Component
public class MovieTitleResolver {

    private final MovieRepository movieRepository;

    public MovieTitleResolver(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    // 여러 영화의 제목을 한 번에(N+1 없이) 가져옵니다. movies에 없는 id는 결과에서 빠집니다.
    public Map<String, String> titlesById(Collection<String> movieIds) {
        if (movieIds.isEmpty()) {
            return Map.of();
        }
        return movieRepository.findAllById(movieIds.stream().distinct().toList()).stream()
                .filter(movie -> movie.getTitle() != null && !movie.getTitle().isBlank())
                .collect(Collectors.toMap(Movie::getMovieId, Movie::getTitle, (first, second) -> first));
    }

    // 한 영화의 제목 - movies에 있으면 그 값, 없으면 fallbackTitle(저장돼 있던/요청에 담긴 값).
    public String titleFor(String movieId, String fallbackTitle) {
        return movieRepository.findById(movieId)
                .map(Movie::getTitle)
                .filter(title -> !title.isBlank())
                .orElse(fallbackTitle);
    }
}
