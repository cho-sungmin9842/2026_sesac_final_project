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
