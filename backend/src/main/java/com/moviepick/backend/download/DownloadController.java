package com.moviepick.backend.download;

import com.moviepick.backend.download.dto.DownloadDto;
import com.moviepick.backend.download.dto.DownloadRequest;
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
@RequestMapping("/api/downloads")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @GetMapping
    public List<DownloadDto> list(@RequestHeader("X-User-Id") Long userId) {
        return downloadService.list(userId);
    }

    @PostMapping
    public DownloadDto add(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody DownloadRequest request) {
        return downloadService.add(userId, request);
    }

    @DeleteMapping("/{movieId}")
    public void remove(@RequestHeader("X-User-Id") Long userId, @PathVariable String movieId) {
        downloadService.remove(userId, movieId);
    }
}
