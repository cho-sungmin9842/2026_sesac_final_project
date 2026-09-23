package com.moviepick.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moviepick.backend.auth.User;

import java.time.LocalDateTime;

public record UserDto(
        Long id,
        String username,
        String nickname,
        @JsonProperty("isAdmin") boolean admin,
        LocalDateTime createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getNickname(), user.isAdmin(), user.getCreatedAt());
    }
}
