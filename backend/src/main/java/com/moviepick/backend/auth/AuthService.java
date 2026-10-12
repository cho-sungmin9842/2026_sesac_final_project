package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.LoginRequest;
import com.moviepick.backend.auth.dto.ResetPasswordRequest;
import com.moviepick.backend.auth.dto.ResetPasswordResponse;
import com.moviepick.backend.auth.dto.SignupRequest;
import com.moviepick.backend.auth.dto.UserDto;
import com.moviepick.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 관리자 계정은 이미 만들어져 있어 일반 가입으로는 더 이상 만들 수 없습니다 - "admin"으로 시작하는
    // 아이디면 관리자 권한이 자동으로 붙는 목업 규칙(isAdminUsername)이 남아있어서, 막아두지 않으면
    // 누구나 그 아이디로 가입해 관리자가 될 수 있었습니다(2026-10-11 지적).
    public UserDto signup(SignupRequest request) {
        String username = normalizeUsername(request.username());
        if (isAdminUsername(username)) {
            throw new ApiException("admin은 관리자 계정입니다.", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByUsername(username)) {
            throw new ApiException("이미 가입된 아이디입니다: " + username, HttpStatus.CONFLICT);
        }
        String nickname = request.nickname().trim();
        if (userRepository.existsByNickname(nickname)) {
            throw new ApiException("이미 사용중인 닉네임입니다: " + nickname, HttpStatus.CONFLICT);
        }
        User user = new User(
                username,
                passwordEncoder.encode(request.password()),
                nickname,
                false
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

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;
    private final SecureRandom secureRandom = new SecureRandom();

    // 이메일/SMS 발송 수단이 없는 데모 인증이라, 아이디+닉네임이 가입 당시 값과 일치하는지만 확인하고
    // ("본인 확인"을 대신하는 최소한의 장치) 서버가 임시 비밀번호를 발급해 바로 그 비밀번호로 바꿔줍니다.
    // 응답에 로그인된 사용자 정보를 함께 담아, 화면에서 비밀번호 입력 없이 바로 로그인 상태로 전환할 수
    // 있게 합니다 - 임시 비밀번호는 로그아웃된 뒤 다시 로그인해야 할 상황을 대비해 화면에 한 번 보여줍니다.
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        String username = normalizeUsername(request.username());
        User user = userRepository.findByUsername(username)
                .filter(candidate -> candidate.getNickname().equals(request.nickname().trim()))
                .orElseThrow(() -> new ApiException("아이디 또는 닉네임이 일치하지 않습니다.", HttpStatus.BAD_REQUEST));

        String tempPassword = generateTempPassword();
        user.changePassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);
        return new ResetPasswordResponse(UserDto.from(user), tempPassword);
    }

    private String generateTempPassword() {
        StringBuilder builder = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            builder.append(TEMP_PASSWORD_CHARS.charAt(secureRandom.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return builder.toString();
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
