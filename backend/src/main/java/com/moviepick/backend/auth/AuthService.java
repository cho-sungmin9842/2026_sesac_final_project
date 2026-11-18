package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.FindUsernameRequest;
import com.moviepick.backend.auth.dto.FindUsernameResponse;
import com.moviepick.backend.auth.dto.LoginRequest;
import com.moviepick.backend.auth.dto.ResetPasswordRequest;
import com.moviepick.backend.auth.dto.ResetPasswordResponse;
import com.moviepick.backend.auth.dto.SignupRequest;
import com.moviepick.backend.auth.dto.UserDto;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.oauth.OAuthUserInfo;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // 아이디 자체가 없는 경우와, 아이디는 맞는데 비밀번호만 틀린 경우를 구분해서 안내합니다
        // (2026-11-17 요청) - 아이디 존재 여부가 드러나는 트레이드오프가 있지만, 사용자 편의를
        // 우선해달라는 명시적 요청에 따른 것입니다.
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException("일치하는 아이디가 없습니다.", HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException("비밀번호가 틀립니다.", HttpStatus.UNAUTHORIZED);
        }
        // 아이디/비밀번호가 맞는지부터 확인한 뒤에 정지 여부를 봐야, 정지된 계정인지 아닌지가 틀린
        // 비밀번호 시도만으로 드러나지 않습니다. 관리자가 고른 정지 사유를 함께 안내합니다(메시지는
        // UserService.buildSuspendedMessage로 통일 - 세션 유효성 재확인(/api/users/me)도 같은 문구).
        if (user.isSuspended()) {
            throw new ApiException(UserService.buildSuspendedMessage(user), HttpStatus.FORBIDDEN);
        }
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

    // 아이디를 잊어버린 사용자용 - 비밀번호 찾기(아이디+닉네임)의 반대 방향으로, 닉네임+비밀번호가
    // 일치하면 본인으로 보고 아이디를 알려줍니다.
    public FindUsernameResponse findUsername(FindUsernameRequest request) {
        User user = userRepository.findByNickname(request.nickname().trim())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ApiException("닉네임 또는 비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST));
        return new FindUsernameResponse(user.getUsername());
    }

    private String generateTempPassword() {
        StringBuilder builder = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            builder.append(TEMP_PASSWORD_CHARS.charAt(secureRandom.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return builder.toString();
    }

    // 구글 로그인(OAuthController 콜백에서 호출) - provider가 내려준 사용자 id로 기존
    // 계정을 찾고, 없으면 그 provider 프로필로 새 계정을 만듭니다. 정지된 계정이면 일반 로그인과 똑같이 막습니다.
    @Transactional
    public UserDto loginOrCreateOAuthUser(String provider, OAuthUserInfo info) {
        User user = userRepository.findByOauthProviderAndOauthProviderId(provider, info.providerUserId())
                .orElseGet(() -> createOAuthUser(provider, info));
        if (user.isSuspended()) {
            throw new ApiException(UserService.buildSuspendedMessage(user), HttpStatus.FORBIDDEN);
        }
        return UserDto.from(user);
    }

    private User createOAuthUser(String provider, OAuthUserInfo info) {
        String username = provider + "_" + info.providerUserId();
        String nickname = resolveAvailableNickname(info.nickname(), provider);
        User user = new User(username, passwordEncoder.encode(generateTempPassword()), nickname, false);
        user.setOauthIdentity(provider, info.providerUserId());
        return userRepository.save(user);
    }

    // provider가 내려준 닉네임이 비어있거나 이미 다른 회원이 쓰고 있으면(닉네임은 유니크 제약) 숫자를
    // 붙여가며 비어있는 닉네임을 찾습니다.
    private String resolveAvailableNickname(String preferredNickname, String provider) {
        String base = (preferredNickname == null || preferredNickname.isBlank())
                ? provider + "_user"
                : preferredNickname.trim();
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByNickname(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    // OAuthController의 /exchange - 콜백에서 발급한 1회성 토큰을 소모한 뒤, 그 토큰에 담겨있던
    // 사용자 id로 실제 로그인 응답(UserDto)을 만들어줍니다.
    public UserDto getUserForOAuthExchange(Long userId) {
        return userRepository.findById(userId)
                .map(UserDto::from)
                .orElseThrow(() -> new ApiException("로그인에 실패했습니다. 다시 시도해주세요.", HttpStatus.UNAUTHORIZED));
    }

    private boolean isAdminUsername(String username) {
        return username.startsWith("admin");
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }
}
