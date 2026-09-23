package com.moviepick.backend.auth;

import com.moviepick.backend.auth.dto.PreferredGenresDto;
import com.moviepick.backend.auth.dto.UpdatePreferredGenresRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/preferred-genres")
public class UserPreferencesController {

    private final UserService userService;

    public UserPreferencesController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PreferredGenresDto get(@RequestHeader("X-User-Id") Long userId) {
        return userService.getPreferredGenres(userId);
    }

    @PutMapping
    public PreferredGenresDto update(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UpdatePreferredGenresRequest request
    ) {
        return userService.updatePreferredGenres(userId, request.genres());
    }
}
