package com.moviepick.backend.recentlyviewed;

import com.moviepick.backend.recentlyviewed.dto.RecentlyViewedMovieDto;
import com.moviepick.backend.recentlyviewed.dto.RecentlyViewedMovieRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recently-viewed")
public class RecentlyViewedMovieController {

    private final RecentlyViewedMovieService recentlyViewedMovieService;

    public RecentlyViewedMovieController(RecentlyViewedMovieService recentlyViewedMovieService) {
        this.recentlyViewedMovieService = recentlyViewedMovieService;
    }

    @GetMapping
    public List<RecentlyViewedMovieDto> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return recentlyViewedMovieService.list(userId, limit);
    }

    @PostMapping
    public void record(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody RecentlyViewedMovieRequest request) {
        recentlyViewedMovieService.record(userId, request);
    }
}
