package com.moviepick.backend.kmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviepick.backend.kmdb.KmdbTextUtils;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KmdbVod {
    private String vodClass;
    private String vodUrl;

    public String cleanClass() {
        return KmdbTextUtils.clean(vodClass);
    }

    public String cleanUrl() {
        return KmdbTextUtils.clean(vodUrl);
    }
}
