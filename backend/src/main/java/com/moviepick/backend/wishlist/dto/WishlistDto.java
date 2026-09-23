package com.moviepick.backend.wishlist.dto;

import com.moviepick.backend.wishlist.Wishlist;

import java.time.LocalDateTime;

public record WishlistDto(
        String movieId,
        String movieTitle,
        String posterUrl,
        LocalDateTime addedAt
) {
    public static WishlistDto from(Wishlist wishlist) {
        return new WishlistDto(
                wishlist.getMovieId(),
                wishlist.getMovieTitle(),
                wishlist.getPosterUrl(),
                wishlist.getAddedAt()
        );
    }
}
