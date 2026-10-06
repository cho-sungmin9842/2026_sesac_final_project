package com.moviepick.backend.movie;

import java.io.Serializable;
import java.util.Objects;

public class MovieCountCacheId implements Serializable {

    private String genreKey;
    private String yearKey;
    private String runtimeKey;

    public MovieCountCacheId() {
    }

    public MovieCountCacheId(String genreKey, String yearKey, String runtimeKey) {
        this.genreKey = genreKey;
        this.yearKey = yearKey;
        this.runtimeKey = runtimeKey;
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
                && Objects.equals(runtimeKey, that.runtimeKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(genreKey, yearKey, runtimeKey);
    }
}
