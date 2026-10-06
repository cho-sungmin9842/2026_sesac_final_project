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
    // 한글 음절/한글 자모/한자/영문/숫자만 "제목의 실제 글자"로 보고, 제목의 맨 앞/맨 뒤에서 이 범위에
    // 들지 않는 문자(공백 포함)가 이어지면 지웁니다. 한자(예: "遺")나 낱자모만으로 된 제목(예: "ㅈ")은
    // 특수문자가 아니라 실제 제목 글자라 지우면 안 되므로 허용 범위(\\u4E00-\\u9FFF 한자, \\u3131-\\u318E
    // 한글 자모)에 포함해둡니다.
    private static final String TITLE_CONTENT_CHAR_CLASS = "0-9A-Za-z가-힣\\u3131-\\u318E\\u4E00-\\u9FFF";
    private static final String EDGE_NON_CONTENT_PATTERN = "[^" + TITLE_CONTENT_CHAR_CLASS + "]";

    private KmdbTextUtils() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll(HIGHLIGHT_MARKER_PATTERN, "").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    // 영화 제목(title) 전용 - "#살아있다"처럼 제목의 맨 앞/맨 뒤에 붙은 특수문자(#, -, +, = 등)만 지우고,
    // "타짜3-짝귀와의 만남"처럼 제목 중간에 있는 특수문자는 실제 표기이므로 그대로 둡니다. "- +"처럼
    // 제목 전체가 맨 앞/맨 뒤 제거 대상 문자뿐이라 다 지우면 빈 제목이 되는 경우는 null을 돌려주고(한자
    // 제목은 특수문자가 아니라 TITLE_CONTENT_CHAR_CLASS에서 이미 허용해두었으니 여기 해당하지 않습니다),
    // 그런 영화는 blank/null 제목 필터링 로직에서 목록에서 자연스럽게 빠집니다.
    public static String cleanTitle(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        String trimmed = cleaned
                .replaceAll("^" + EDGE_NON_CONTENT_PATTERN + "+", "")
                .replaceAll(EDGE_NON_CONTENT_PATTERN + "+$", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static List<String> splitPipe(String value) {
        return splitBy(value, "\\|");
    }

    public static List<String> splitComma(String value) {
        return splitBy(value, ",");
    }

    // nation 파라미터는 부분일치라 "대한민국,미국"처럼 해외와의 공동제작도 걸립니다. "국내 영화만"
    // 보여주려면 nation 값이 정확히 "대한민국" 하나뿐인지 확인해야 합니다(2026-10-06, 공동제작까지
    // 포함하면 "국내 영화" 집계/목록에 해외 공동제작작이 섞여 들어가므로 단독인 경우만 인정).
    public static boolean isDomesticOnlyNation(String nation) {
        List<String> nations = splitComma(nation);
        return nations.size() == 1 && "대한민국".equals(nations.get(0));
    }

    // 영화 목록 화면의 "러닝타임" 필터("2시간 미만"/"2시간 이상") 기준값(분)입니다.
    public static final int RUNTIME_FILTER_THRESHOLD_MINUTES = 120;

    public static Integer parseRuntimeMinutes(String runtime) {
        String cleaned = clean(runtime);
        if (cleaned == null || !cleaned.matches("\\d+")) {
            return null;
        }
        return Integer.parseInt(cleaned);
    }

    // runtimeFilter는 "under120"/"over120" 둘 중 하나거나(필터 없음은 null/빈 값) 들어옵니다. 상영시간이
    // 없는(runtimeMinutes null) 영화는 "전체"에는 포함되지만, "미만"/"이상" 어느 쪽에도 속한다고 단정할 수
    // 없으므로 구체적인 필터가 걸려 있을 땐 제외합니다.
    public static boolean matchesRuntimeFilter(Integer runtimeMinutes, String runtimeFilter) {
        if (runtimeFilter == null || runtimeFilter.isBlank()) {
            return true;
        }
        if (runtimeMinutes == null) {
            return false;
        }
        return "under120".equals(runtimeFilter)
                ? runtimeMinutes < RUNTIME_FILTER_THRESHOLD_MINUTES
                : runtimeMinutes >= RUNTIME_FILTER_THRESHOLD_MINUTES;
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
