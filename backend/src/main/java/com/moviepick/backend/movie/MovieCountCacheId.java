package com.moviepick.backend.movie;

import java.io.Serializable;
import java.util.Objects;

public class MovieCountCacheId implements Serializable {

    private String genreKey;
    private String yearKey;
    private String runtimeKey;
    private String ageRatingKey;

    public MovieCountCacheId() {
    }

    public MovieCountCacheId(String genreKey, String yearKey, String runtimeKey, String ageRatingKey) {
        this.genreKey = genreKey;
        this.yearKey = yearKey;
        this.runtimeKey = runtimeKey;
        this.ageRatingKey = ageRatingKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MovieCountCacheId that)) {
            return false;
        }
        return Objects.equals(genreKey, that.genreKey)
                && Objects.equals(yearKey, that.yearKey)
                && Objects.equals(runtimeKey, that.runtimeKey)
                && Objects.equals(ageRatingKey, that.ageRatingKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(genreKey, yearKey, runtimeKey, ageRatingKey);
    }
}
