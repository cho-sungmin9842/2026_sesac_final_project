package com.moviepick.backend.movie;

import com.moviepick.backend.movie.dto.MovieSuggestionDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, String> {

    // 헤더 검색창 자동완성 - KMDB를 다시 부르지 않고, 이미 캐시해둔(배치 스캔으로 사실상 전체 카탈로그가
    // 채워져 있는) movies 테이블의 title만 바로 조회합니다(수만 건이어도 응답이 즉시 옵니다). "가장
    // 가까운 것"을 우선순위로 매길 때, 1) 제목이 입력값과 완전히 같은 것 2) 입력값으로 시작하는 것
    // 3) 입력값을 중간에 포함하는 것 순으로 두고, 같은 순위 안에서는 제목이 짧을수록(입력값에 더 가까운
    // 길이일수록) 먼저 보여줍니다 - 그래야 "её" 한 글자처럼 아주 흔한 부분 일치보다 실제로 그 글자로
    // 시작하는 영화가 먼저 뜹니다.
    @Query(value = """
            SELECT movie_id, title, year, poster_url
            FROM movies
            WHERE title LIKE CONCAT('%', :query, '%')
            ORDER BY
                CASE
                    WHEN title = :query THEN 0
                    WHEN title LIKE CONCAT(:query, '%') THEN 1
                    ELSE 2
                END,
                LENGTH(title) ASC,
                title ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTitleSuggestionsRaw(@Param("query") String query, @Param("limit") int limit);

    default List<MovieSuggestionDto> findTitleSuggestions(String query, int limit) {
        return findTitleSuggestionsRaw(query, limit).stream()
                .map(row -> new MovieSuggestionDto(
                        (String) row[0],
                        (String) row[1],
                        row[2] == null ? null : ((Number) row[2]).intValue(),
                        (String) row[3]))
                .toList();
    }
}
