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
    // 한글/영문/숫자/공백만 남기고 나머지는 전부 지웁니다(#, -, +, :, !, ? 등).
    private static final String SPECIAL_CHAR_PATTERN = "[^0-9A-Za-z가-힣\\s]";

    private KmdbTextUtils() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll(HIGHLIGHT_MARKER_PATTERN, "").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    // 영화 제목(title) 전용 - "#살아있다"의 "#"이나 "가문의 영광 3-며느리 전성시대"의 "-"처럼 제목에 섞여
    // 있는 특수문자를 지운 뒤 화면에 보여줍니다. 다만 "- +"나 "遺"(한자 한 글자)처럼 제목 자체가 특수문자/
    // 한자 등으로만 이루어진 경우 다 지우면 빈 제목이 되어버리므로, 그럴 때는 원래 제목을 그대로 둡니다.
    public static String cleanTitle(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        String withoutSpecialChars = cleaned.replaceAll(SPECIAL_CHAR_PATTERN, "").replaceAll("\\s+", " ").trim();
        return withoutSpecialChars.isEmpty() ? cleaned : withoutSpecialChars;
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
