package com.moviepick.backend.watched;

import com.moviepick.backend.watched.dto.WatchedMovieDto;
import com.moviepick.backend.watched.dto.WatchedMovieRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watched")
public class WatchedMovieController {

    private final WatchedMovieService watchedMovieService;

    public WatchedMovieController(WatchedMovieService watchedMovieService) {
        this.watchedMovieService = watchedMovieService;
    }

    @GetMapping
    public List<WatchedMovieDto> list(@RequestHeader("X-User-Id") Long userId) {
        return watchedMovieService.list(userId);
    }

    @GetMapping("/{movieId}")
    public boolean isWatched(@RequestHeader("X-User-Id") Long userId, @PathVariable String movieId) {
        return watchedMovieService.isWatched(userId, movieId);
    }

    @PostMapping
    public WatchedMovieDto add(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody WatchedMovieRequest request) {
        return watchedMovieService.add(userId, request);
    }

    @DeleteMapping("/{movieId}")
    public void remove(@RequestHeader("X-User-Id") Long userId, @PathVariable String movieId) {
        watchedMovieService.remove(userId, movieId);
    }
}
