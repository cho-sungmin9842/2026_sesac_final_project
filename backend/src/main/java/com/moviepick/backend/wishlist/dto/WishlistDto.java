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
        return from(wishlist, wishlist.getMovieTitle());
    }

    // movieTitle: 화면에 보여줄 제목(movies 기준으로 맞춘 값). 저장돼 있던 제목과 다를 수 있어서 따로 받습니다.
    public static WishlistDto from(Wishlist wishlist, String movieTitle) {
        return new WishlistDto(
                wishlist.getMovieId(),
                movieTitle,
                wishlist.getPosterUrl(),
                wishlist.getAddedAt()
        );
    }
}
