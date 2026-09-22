package com.moviepick.backend.kmdb;

import java.util.Arrays;
import java.util.List;

/**
 * KMDB Open API 응답 값 정제 유틸리티.
 * <p>
 * KMDB는 검색어와 일치한 구간의 앞뒤를 {@code !HS} / {@code !HE} 마커로 감싸서 표시하고
 * (예: {@code "!HS 기생충 !HE"}), 포스터/스틸컷처럼 값이 여러 개인 필드는 {@code |}로,
 * 장르/국가처럼 목록성 필드는 {@code ,}로 구분해 내려줍니다.
 * 실제 서비스키로 호출한 응답(2026-09-18 확인)을 기준으로 검증되었습니다.
 */
public final class KmdbTextUtils {

    private static final String HIGHLIGHT_MARKER_PATTERN = "!HS|!HE";

    private KmdbTextUtils() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll(HIGHLIGHT_MARKER_PATTERN, "").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static List<String> splitPipe(String value) {
        return splitBy(value, "\\|");
    }

    public static List<String> splitComma(String value) {
        return splitBy(value, ",");
    }

    private static List<String> splitBy(String value, String regex) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return List.of();
        }
        return Arrays.stream(cleaned.split(regex))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
