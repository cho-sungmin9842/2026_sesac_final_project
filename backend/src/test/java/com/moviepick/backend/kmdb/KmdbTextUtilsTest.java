package com.moviepick.backend.kmdb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmdbTextUtilsTest {

    @Test
    void cleanRemovesHighlightMarkerAndTrims() {
        assertThat(KmdbTextUtils.clean("!HS 기생충 !HE")).isEqualTo("기생충");
        assertThat(KmdbTextUtils.clean("  ")).isNull();
        assertThat(KmdbTextUtils.clean(null)).isNull();
    }

    // 검색어가 단어 전체(기생충)가 아니라 복합어의 어간만("학교"가 "학교는"의 일부) 매치되면, KMDB가
    // 마커 템플릿 공백을 그 복합어 내부에 그대로 남겨 "학교 는"처럼 잘못된 띄어쓰기로 내려줍니다
    // (2026-10-14 실측 - title=학교로 KMDB를 직접 호출해 "!HS 학교 !HE 는" 원문 확인). 단어 사이의
    // 진짜 공백은 그대로 지켜지는지도 함께 검증합니다.
    @Test
    void cleanRemovesStrayMarkerSpaceInsideCompoundWord() {
        assertThat(KmdbTextUtils.clean(" 지금 우리  !HS 학교 !HE 는")).isEqualTo("지금 우리 학교는");
        assertThat(KmdbTextUtils.clean("  !HS 지금 !HE   !HS 우리 !HE   !HS 학교는 !HE "))
                .isEqualTo("지금 우리 학교는");
    }

    @Test
    void splitPipeParsesMultipleUrls() {
        assertThat(KmdbTextUtils.splitPipe("a.jpg|b.jpg|c.jpg"))
                .containsExactly("a.jpg", "b.jpg", "c.jpg");
        assertThat(KmdbTextUtils.splitPipe(null)).isEqualTo(List.of());
    }

    @Test
    void splitCommaParsesGenreList() {
        assertThat(KmdbTextUtils.splitComma("드라마,코미디"))
                .containsExactly("드라마", "코미디");
    }
}
