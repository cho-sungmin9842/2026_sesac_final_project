package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.ChangePasswordRequest;
import com.moviepick.backend.auth.dto.DeleteAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
