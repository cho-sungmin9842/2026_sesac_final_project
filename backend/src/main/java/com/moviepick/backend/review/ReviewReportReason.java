package com.moviepick.backend.review;

import com.moviepick.backend.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

// 리뷰 신고 사유 목록입니다. 프론트(frontend/src/pages/MovieDetail/components/reviewReportReasons.js)의
// 목록과 코드(name)가 반드시 같아야 합니다.
public enum ReviewReportReason {
    ABUSIVE_LANGUAGE("욕설·비방·혐오 표현"),
    SPAM_AD("스팸/광고성 내용"),
    SPOILER("스포일러 포함"),
    FALSE_INFO("허위 정보 또는 영화와 무관한 내용");

    private final String label;

    ReviewReportReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ReviewReportReason fromCode(String code) {
        return Arrays.stream(values())
                .filter(reason -> reason.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new ApiException("올바르지 않은 신고 사유입니다: " + code, HttpStatus.BAD_REQUEST));
    }
}
