package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.ChangePasswordRequest;
import com.moviepick.backend.auth.dto.DeleteAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserAccountController {

    private final UserService userService;

    public UserAccountController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 로그인된 화면이 주기적으로 호출해 세션이 여전히 유효한지(계정이 남아있고 정지되지 않았는지)
     * 확인합니다. 정지됐으면 403으로 응답해 프론트가 강제 로그아웃시킵니다(AuthContext.jsx 참고).
     */
    @GetMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void checkSession(@RequestHeader("X-User-Id") Long userId) {
        userService.verifySessionActive(userId);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
    }

    /**
     * 마이페이지 "회원 탈퇴" - 비밀번호 확인 후 계정과 관련 데이터를 전부 삭제합니다.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(userId, request.password());
    }
}
