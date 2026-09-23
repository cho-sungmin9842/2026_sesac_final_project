package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.LoginRequest;
import com.moviepick.backend.auth.dto.SignupRequest;
import com.moviepick.backend.auth.dto.UserDto;
import com.moviepick.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 목업 1번 화면 규칙: 아이디가 "admin"으로 시작하면 관리자 계정입니다.
    public UserDto signup(SignupRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new ApiException("이미 가입된 아이디입니다: " + username, HttpStatus.CONFLICT);
        }
        User user = new User(
                username,
                passwordEncoder.encode(request.password()),
                request.nickname().trim(),
                isAdminUsername(username)
        );
        return UserDto.from(userRepository.save(user));
    }

    public UserDto login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        User user = userRepository.findByUsername(username)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ApiException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED));
        return UserDto.from(user);
    }

    // 카카오/네이버/구글 버튼용 - 실제 OAuth 없이 provider별 고정 데모 계정을 최초 클릭 시 만들고, 이후엔 재사용합니다.
    public UserDto loginOrCreateSocialUser(String provider) {
        String username = provider.trim().toLowerCase() + "-user";
        return userRepository.findByUsername(username)
                .map(UserDto::from)
                .orElseGet(() -> {
                    User user = new User(
                            username,
                            passwordEncoder.encode("social-login-" + provider),
                            provider,
                            isAdminUsername(username)
                    );
                    return UserDto.from(userRepository.save(user));
                });
    }

    private boolean isAdminUsername(String username) {
        return username.startsWith("admin");
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }
}
