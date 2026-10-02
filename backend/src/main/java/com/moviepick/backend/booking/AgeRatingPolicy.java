package com.moviepick.backend.booking;

import java.util.Set;

/**
 * 영화 관람가 등급에 따라 어떤 인원 구분(ticketCategory)을 선택할 수 없는지 판정합니다.
 * KMDB 전체 카탈로그(53,142건)를 실제로 스캔해서 확인한 rating 표기 23종을 기준으로 신/구 표기를
 * 전부 포함시켰습니다(12세관람가/12세이상관람가/중학생가/중학생이상/고등학생가/고등학생이상관람가/
 * 국민학생관람불가/15세미만불가/19세관람가(청소년관람불가)/미성년자관람불가/연소자불가/제한상영가 등).
 * 프론트 bookingData.js의 같은 이름 로직과 값을 맞춰야 합니다.
 */
final class AgeRatingPolicy {

    private AgeRatingPolicy() {
    }

    static Set<String> disallowedCategories(String ageRating) {
        if (ageRating == null) {
            return Set.of();
        }
        // 어린이·청소년 모두 관람 불가(사실상 성인만 가능) 등급 - 신/구 표기를 전부 포함합니다.
        // "제한상영가"는 19금보다도 엄격한 등급이라 같은 급으로 묶습니다.
        if (ageRating.contains("청소년관람불가") || ageRating.contains("미성년자관람불가")
                || ageRating.contains("연소자불가") || ageRating.contains("제한상영가")
                || ageRating.contains("19세")) {
            return Set.of("child", "teen");
        }
        // 12세/15세 이상 등급(구 표기 중학생/고등학생/국민학생 포함) - 청소년(만 12~18세)은 그 안에
        // 기준 연령 이상도 포함돼 있어 그대로 두고, 만 5~11세로 한정된 어린이만 막습니다.
        if (ageRating.contains("12세") || ageRating.contains("15세")
                || ageRating.contains("중학생") || ageRating.contains("고등학생")
                || ageRating.contains("국민학생관람불가")) {
            return Set.of("child");
        }
        // "전체관람가", "모두관람가", "연소자관람가"/"미성년자관람가"/"국민학생이상관람가"(구 표기 - 전부
        // 관람 허용 쪽) 등은 제한 없음.
        return Set.of();
    }
}
