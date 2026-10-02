package com.moviepick.backend.review;

public record TopRatedMovie(String movieId, double averageScore, long reviewCount) {
}
