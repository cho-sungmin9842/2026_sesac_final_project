package com.moviepick.backend.kmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviepick.backend.kmdb.KmdbTextUtils;
import lombok.Getter;
import lombok.Setter;

/**
 * KMDB의 감독/배우 정보. 배우일 경우에만 cast(배역명)가 채워집니다.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KmdbPerson {
    private String directorNm;
    private String directorEnNm;
    private String directorId;
    private String actorNm;
    private String actorEnNm;
    private String actorId;
    private String cast;

    public String displayName() {
        String name = directorNm != null ? directorNm : actorNm;
        return KmdbTextUtils.clean(name);
    }
}
