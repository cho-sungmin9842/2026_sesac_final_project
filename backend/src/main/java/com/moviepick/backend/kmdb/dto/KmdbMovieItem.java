package com.moviepick.backend.kmdb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * KMDB(한국영상자료원) Open API의 영화 1건 응답을 그대로 옮긴 DTO.
 * <p>
 * 필드 구성은 공개된 KMDB Open API 문서 기준이며, 실제 서비스키로 호출한 응답과 다른 필드가 있다면
 * 이 클래스만 수정하면 됩니다. {@code ignoreUnknown = true}라 모르는 필드가 와도 역직렬화는 깨지지 않습니다.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KmdbMovieItem {
    private String movieId;
    private String movieSeq;
    private String title;
    private String titleEng;
    private String titleOrg;
    private String prodYear;
    private String nation;
    private String company;
    private String genre;
    private String runtime;
    private String rating;
    private String type;
    private String keywords;
    private String posters;
    private String stlls;
    private KmdbDirectorContainer directors;
    private KmdbActorContainer actors;
    private KmdbPlotContainer plots;
    private KmdbVodContainer vods;
}
