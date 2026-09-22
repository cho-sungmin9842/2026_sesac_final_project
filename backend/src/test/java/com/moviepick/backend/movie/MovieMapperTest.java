package com.moviepick.backend.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviepick.backend.kmdb.dto.KmdbMovieItem;
import com.moviepick.backend.kmdb.dto.KmdbSearchResponse;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KMDB 실제 서비스키 없이도, 문서 기준으로 가정한 JSON 스키마와 매핑 로직이 서로 맞물리는지 검증합니다.
 * 실제 응답 구조가 다르면 kmdb-sample-response.json과 KmdbMovieItem 계열 DTO를 함께 맞춰주세요.
 */
class MovieMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MovieMapper movieMapper = new MovieMapper();

    @Test
    void parsesSampleResponseAndMapsToSummaryAndDetail() throws Exception {
        KmdbMovieItem item = firstItemFromFixture();

        MovieSummaryDto summary = movieMapper.toSummary(item);
        assertThat(summary.id()).isEqualTo("K19980001_01");
        assertThat(summary.title()).isEqualTo("기생충");
        assertThat(summary.englishTitle()).isEqualTo("Parasite");
        assertThat(summary.year()).isEqualTo(2019);
        assertThat(summary.genres()).containsExactly("드라마", "코미디");
        assertThat(summary.posterUrl()).isEqualTo("http://kmdb.or.kr/poster1.jpg");
        assertThat(summary.ageRating()).isEqualTo("15세이상관람가");
        assertThat(summary.runtimeMinutes()).isEqualTo(132);

        MovieDetailDto detail = movieMapper.toDetail(item);
        assertThat(detail.directors()).containsExactly("봉준호");
        assertThat(detail.actors()).extracting("name").containsExactly("송강호", "이선균");
        assertThat(detail.actors()).extracting("role").containsExactly("기택 역", "박사장 역");
        assertThat(detail.plot()).startsWith("전원백수인 기택네 가족은");
        assertThat(detail.runtimeMinutes()).isEqualTo(132);
        assertThat(detail.posterUrl()).isEqualTo("http://kmdb.or.kr/poster1.jpg");
        assertThat(detail.trailers()).hasSize(1);
        assertThat(detail.trailers().get(0).label()).isEqualTo("예고편 [1차예고편]");
        assertThat(detail.trailers().get(0).url())
                .isEqualTo("https://www.kmdb.or.kr/trailer/trailerPlayPop?pFileNm=MK041673_P02.mp4");
    }

    @Test
    void fallsBackToStillCutWhenNoPosterExists() {
        KmdbMovieItem item = new KmdbMovieItem();
        item.setMovieId("K99999999");
        item.setMovieSeq("01");
        item.setTitle("포스터 없는 영화");
        item.setPosters("");
        item.setStlls("http://kmdb.or.kr/still1.jpg|http://kmdb.or.kr/still2.jpg");

        assertThat(movieMapper.toSummary(item).posterUrl()).isEqualTo("http://kmdb.or.kr/still1.jpg");
        assertThat(movieMapper.toDetail(item).posterUrl()).isEqualTo("http://kmdb.or.kr/still1.jpg");
    }

    @Test
    void posterUrlIsNullWhenNeitherPosterNorStillExists() {
        KmdbMovieItem item = new KmdbMovieItem();
        item.setMovieId("K99999998");
        item.setMovieSeq("01");
        item.setTitle("아무 이미지도 없는 영화");

        assertThat(movieMapper.toSummary(item).posterUrl()).isNull();
        assertThat(movieMapper.toDetail(item).posterUrl()).isNull();
    }

    private KmdbMovieItem firstItemFromFixture() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("kmdb-sample-response.json")) {
            KmdbSearchResponse response = objectMapper.readValue(stream, KmdbSearchResponse.class);
            return response.allItems().get(0);
        }
    }
}
