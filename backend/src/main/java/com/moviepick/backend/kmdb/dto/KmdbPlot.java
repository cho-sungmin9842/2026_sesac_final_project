package com.moviepick.backend.kmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviepick.backend.kmdb.KmdbTextUtils;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KmdbPlot {
    private String plotLang;
    private String plotText;

    public String cleanText() {
        return KmdbTextUtils.clean(plotText);
    }
}
