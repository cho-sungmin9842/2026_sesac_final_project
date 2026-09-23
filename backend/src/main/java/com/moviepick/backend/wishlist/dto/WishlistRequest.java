package com.moviepick.backend.wishlist.dto;

import jakarta.validation.constraints.NotBlank;

public record WishlistRequest(
        @NotBlank String movieId,
        @NotBlank String movieTitle,
        String posterUrl
) {
}
