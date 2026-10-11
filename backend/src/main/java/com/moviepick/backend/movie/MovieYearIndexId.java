package com.moviepick.backend.movie;

import java.io.Serializable;
import java.util.Objects;

public class MovieYearIndexId implements Serializable {

    private String genreKey;
    private String yearKey;
    private String movieId;

    public MovieYearIndexId() {
    }

    public MovieYearIndexId(String genreKey, String yearKey, String movieId) {
        this.genreKey = genreKey;
        this.yearKey = yearKey;
        this.movieId = movieId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MovieYearIndexId that)) {
            return false;
        }
        return Objects.equals(genreKey, that.genreKey)
                && Objects.equals(yearKey, that.yearKey)
                && Objects.equals(movieId, that.movieId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(genreKey, yearKey, movieId);
    }
}
