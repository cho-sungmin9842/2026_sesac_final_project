package com.moviepick.backend.common;

/**
 * 단어의 받침 유무에 따라 "을/를", "이/가", "은/는", "과/와", "으로/로" 같은 조사를 자동으로 골라줍니다.
 * ChatService의 여러 응답 문구가 "...을(를) 찜했습니다.", "...(으)로 변경했습니다."처럼 조사 표기
 * placeholder를 그대로 화면에 띄워버린 문제가 있었는데(2026-11-01 실측), 한글 음절의 유니코드
 * 코드포인트로 종성(받침) 유무를 계산해 매번 정확한 조사를 붙이도록 공통 유틸로 뽑았습니다.
 */
public final class KoreanJosa {

    private static final int HANGUL_SYLLABLE_START = 0xAC00;
    private static final int HANGUL_SYLLABLE_END = 0xD7A3;

    private KoreanJosa() {
    }

    private static boolean hasBatchim(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        String trimmed = word.trim();
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        // 한글 음절이 아니면(영문/숫자/기타 문자로 끝나면) 받침이 없는 것으로 취급합니다.
        if (lastChar < HANGUL_SYLLABLE_START || lastChar > HANGUL_SYLLABLE_END) {
            return false;
        }
        return (lastChar - HANGUL_SYLLABLE_START) % 28 != 0;
    }

    // pick("아이디", "을", "를") -> "를", pick("선생님", "을", "를") -> "을"
    public static String pick(String word, String withBatchim, String withoutBatchim) {
        return hasBatchim(word) ? withBatchim : withoutBatchim;
    }
}
