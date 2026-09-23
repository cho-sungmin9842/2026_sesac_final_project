package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.PreferredGenresDto;
import com.moviepick.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// AuthService(회원가입/로그인)와 분리 - 이건 이미 로그인된 사용자의 프로필 설정을 다룹니다.
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public PreferredGenresDto getPreferredGenres(Long userId) {
        return PreferredGenresDto.from(requireUser(userId));
    }

    @Transactional
    public PreferredGenresDto updatePreferredGenres(Long userId, List<String> genres) {
        User user = requireUser(userId);
        Set<String> cleaned = genres.stream()
                .map(String::trim)
                .filter(genre -> !genre.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        user.updatePreferredGenres(cleaned);
        return PreferredGenresDto.from(userRepository.save(user));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
    }
}
