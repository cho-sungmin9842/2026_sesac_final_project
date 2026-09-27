package com.moviepick.backend.kmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviepick.backend.kmdb.KmdbTextUtils;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KmdbRating {
    // yyyyMMdd 형식(예: "20250115").
    private String releaseDate;

    public String cleanReleaseDate() {
        return KmdbTextUtils.clean(releaseDate);
    }
}
