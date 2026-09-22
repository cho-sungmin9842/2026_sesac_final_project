package com.moviepick.backend.movie;

import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSearchResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping("/api/movies")
    public MovieSearchResultDto search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String year,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return movieService.search(query, genre, year, page, pageSize);
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
