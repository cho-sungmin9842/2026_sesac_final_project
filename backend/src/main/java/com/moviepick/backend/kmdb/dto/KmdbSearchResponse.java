package com.moviepick.backend.kmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KmdbSearchResponse {
    @JsonProperty("TotalCount")
    private Integer totalCount;

    @JsonProperty("Query")
    private String query;

    @JsonProperty("Data")
    private List<KmdbDataBlock> data;

    public List<KmdbMovieItem> allItems() {
        if (data == null) {
            return List.of();
        }
        return data.stream()
                .filter(block -> block.getResult() != null)
                .flatMap(block -> block.getResult().stream())
                .toList();
    }
}
