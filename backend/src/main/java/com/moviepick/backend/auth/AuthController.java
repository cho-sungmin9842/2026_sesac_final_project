package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.LoginRequest;
import com.moviepick.backend.auth.dto.SignupRequest;
import com.moviepick.backend.auth.dto.SocialLoginRequest;
import com.moviepick.backend.auth.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public UserDto signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public UserDto login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/social")
    public UserDto socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        return authService.loginOrCreateSocialUser(request.provider());
    }
}
