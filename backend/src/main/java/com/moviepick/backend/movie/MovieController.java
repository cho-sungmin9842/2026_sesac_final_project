package com.moviepick.backend.movie;

import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSearchResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    // genre는 홈 화면 "취향저격 신작"처럼 선호 장르가 여러 개일 때 ?genre=코미디&genre=스릴러 형태로
    // 반복 전달될 수 있어 List<String>으로 받습니다.
    @GetMapping("/api/movies")
    public MovieSearchResultDto search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) List<String> genre,
            @RequestParam(required = false) String year,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return movieService.search(query, genre, year, sort, page, pageSize);
    }

    /**
     * 예매 화면의 "상영중인 영화" 목록. 최근 2개월(오늘 기준 releaseDts~releaseDte)에 개봉한 영화를 KMDB에서 가져옵니다.
     */
    @GetMapping("/api/movies/now-showing")
    public MovieSearchResultDto getNowShowing(@RequestParam(defaultValue = "10") int listCount) {
        return movieService.getNowShowing(listCount);
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
}
