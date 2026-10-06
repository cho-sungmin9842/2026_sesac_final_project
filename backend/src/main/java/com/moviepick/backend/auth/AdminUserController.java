package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.UserAdminDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 관리자 "회원 관리" 화면 전용 - 요청자가 실제로 관리자인지는 UserService에서 X-User-Id로 확인합니다.
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserAdminDto> listAll(@RequestHeader("X-User-Id") Long userId) {
        return userService.listAllForAdmin(userId);
    }
}
