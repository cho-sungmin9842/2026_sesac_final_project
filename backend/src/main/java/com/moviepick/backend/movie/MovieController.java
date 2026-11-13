package com.moviepick.backend.movie;

import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.dto.AiSummaryDto;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSearchResultDto;
import com.moviepick.backend.movie.dto.MovieSuggestionDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MovieController {

    private final MovieService movieService;
    private final AiSummaryService aiSummaryService;

    public MovieController(MovieService movieService, AiSummaryService aiSummaryService) {
        this.movieService = movieService;
        this.aiSummaryService = aiSummaryService;
    }

    // genre는 홈 화면 "취향저격 신작"처럼 선호 장르가 여러 개일 때 ?genre=코미디&genre=스릴러 형태로
    // 반복 전달될 수 있어 List<String>으로 받습니다.
    @GetMapping("/api/movies")
    public MovieSearchResultDto search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) List<String> genre,
            @RequestParam(required = false) String year,
            // "under120"/"over120" 둘 중 하나거나, 필터 없음("전체")은 생략됩니다.
            @RequestParam(required = false) String runtime,
            // KmdbTextUtils의 general/age12/age15/age19/unknown 중 하나거나, 필터 없음("전체")은 생략됩니다.
            @RequestParam(required = false) String ageRating,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            // 검색창의 영화/배우/감독 드롭다운 선택값. KMDB 파라미터명과 동일하게 title/actor/director를 받습니다.
            @RequestParam(defaultValue = "title") String field,
            // 포스터/관람등급 정보가 없는 영화를 결과에서 아예 빼고, 그만큼 다음 데이터를 더 받아와 한
            // 페이지를 꽉 채웁니다(프론트에서 받은 뒤 걸러내면 그 페이지가 빈 자리투성이가 됩니다).
            @RequestParam(defaultValue = "false") boolean hideIncomplete
    ) {
        return movieService.search(query, genre, year, runtime, ageRating, sort, page, pageSize, field, hideIncomplete);
    }

    /**
     * 장르 필터 사이드바/마이페이지 선호 장르 선택지. MovieGenres.ALL 하나를 프론트와 AI 채팅(ChatService)이
     * 공통으로 쓰는 출처로 삼아, 한쪽만 고쳐서 둘이 어긋나는 일이 없게 합니다.
     */
    @GetMapping("/api/movies/genres")
    public List<String> getGenres() {
        return MovieGenres.ALL;
    }

    /**
     * 헤더 검색창 자동완성(제목 검색 전용). 영화 제목 후보를 돌려줍니다. limit은 프론트가 5로 고정해
     * 넘겨줍니다.
     */
    @GetMapping("/api/movies/suggestions")
    public List<MovieSuggestionDto> getSuggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return movieService.suggestMovies(query, limit);
    }

    /**
     * 헤더 검색창 자동완성(배우/감독 검색 전용). 영화 제목이 아니라 실제 "사람 이름" 후보를 돌려줍니다
     * (field=actor 또는 director만 지원 - 키워드 검색은 자동완성을 지원하지 않습니다).
     */
    @GetMapping("/api/movies/person-suggestions")
    public List<String> getPersonSuggestions(
            @RequestParam String query,
            @RequestParam String field,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return movieService.suggestPersonNames(query, field, limit);
    }

    /**
     * 예매 화면의 "상영중인 영화" 목록. 최근 4주(오늘 기준 releaseDts~releaseDte)에 개봉한 영화를 KMDB에서 가져옵니다.
     */
    @GetMapping("/api/movies/now-showing")
    public MovieSearchResultDto getNowShowing(@RequestParam(defaultValue = "8") int listCount) {
        return movieService.getNowShowing(listCount);
    }

    /**
     * 홈 화면 "지금 인기 있는 영화". 사용자 리뷰 유무와 무관하게, KMDB에서 접속 시점 기준(releaseDts=오늘)
     * 이후 개봉(예정)인 작품을 그대로 보여줍니다(리뷰 평점 기반으로 걸렀을 때 막 개봉한 신작이 전혀
     * 노출되지 않는 문제가 있어 2026-11-08에 교체). KMDB가 응답한 건수를 자르지 않고 전부 돌려줍니다.
     */
    @GetMapping("/api/movies/popular")
    public List<MovieSummaryDto> getPopular() {
        return movieService.getPopularMovies();
    }

    /**
     * 목록에서 내려준 합성 id("{movieId}_{movieSeq}") 그대로 넘겨받아 상세 정보를 조회합니다.
     */
    @GetMapping("/api/movies/{id}")
    public MovieDetailDto getDetail(@PathVariable String id) {
        String[] parts = id.split("_", 2);
        if (parts.length != 2) {
            throw new ApiException("잘못된 영화 id 형식입니다: " + id, HttpStatus.BAD_REQUEST);
        }
        return movieService.getDetail(parts[0], parts[1]);
    }

    /**
     * 상세 화면 "AI 줄거리 요약 (스포방지)" 버튼용. Gemini로 결말/반전을 뺀 짧은 요약을 만들어줍니다.
     */
    @GetMapping("/api/movies/{id}/ai-summary")
    public AiSummaryDto getAiSummary(@PathVariable String id) {
        return new AiSummaryDto(aiSummaryService.summarize(id));
    }
}
