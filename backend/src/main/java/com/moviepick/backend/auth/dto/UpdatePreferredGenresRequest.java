package com.moviepick.backend.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdatePreferredGenresRequest(@NotNull List<String> genres) {
}
