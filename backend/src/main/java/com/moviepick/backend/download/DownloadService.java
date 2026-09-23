package com.moviepick.backend.download;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.download.dto.DownloadDto;
import com.moviepick.backend.download.dto.DownloadRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DownloadService {

    private final DownloadRepository downloadRepository;
    private final UserRepository userRepository;

    public DownloadService(DownloadRepository downloadRepository, UserRepository userRepository) {
        this.downloadRepository = downloadRepository;
        this.userRepository = userRepository;
    }

    public List<DownloadDto> list(Long userId) {
        return downloadRepository.findByUserIdOrderByDownloadedAtDesc(userId).stream()
                .map(DownloadDto::from)
                .toList();
    }

    // 이미 받은 영화를 다시 요청해도 에러 없이 기존 기록을 그대로 돌려줍니다(프론트의 "이미 있으면 무시" 동작과 동일).
    public DownloadDto add(Long userId, DownloadRequest request) {
        return downloadRepository.findByUserIdAndMovieId(userId, request.movieId())
                .map(DownloadDto::from)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
                    Download download = new Download(
                            user, request.movieId(), request.movieTitle(), request.posterUrl(), request.sizeMb()
                    );
                    return DownloadDto.from(downloadRepository.save(download));
                });
    }

    // 파생 삭제 쿼리(deleteByUserIdAndMovieId)는 SimpleJpaRepository의 기본 CRUD 메서드와 달리
    // 자체적으로 트랜잭션을 열어주지 않아서, 호출부에 명시적으로 @Transactional이 필요합니다.
    @Transactional
    public void remove(Long userId, String movieId) {
        downloadRepository.deleteByUserIdAndMovieId(userId, movieId);
    }
}
