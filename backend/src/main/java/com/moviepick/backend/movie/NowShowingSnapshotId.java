package com.moviepick.backend.movie;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class NowShowingSnapshotId implements Serializable {

    private LocalDate snapshotDate;
    private String movieId;

    public NowShowingSnapshotId() {
    }

    public NowShowingSnapshotId(LocalDate snapshotDate, String movieId) {
        this.snapshotDate = snapshotDate;
        this.movieId = movieId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NowShowingSnapshotId that)) {
            return false;
        }
        return Objects.equals(snapshotDate, that.snapshotDate) && Objects.equals(movieId, that.movieId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotDate, movieId);
    }
}
