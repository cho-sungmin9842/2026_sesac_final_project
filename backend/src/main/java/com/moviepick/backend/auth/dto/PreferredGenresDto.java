package com.moviepick.backend.auth.dto;

import com.moviepick.backend.auth.User;

import java.util.List;

public record PreferredGenresDto(List<String> genres) {
    public static PreferredGenresDto from(User user) {
        return new PreferredGenresDto(List.copyOf(user.getPreferredGenres()));
    }
}
