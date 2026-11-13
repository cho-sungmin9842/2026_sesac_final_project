package com.moviepick.backend.auth;

import com.moviepick.backend.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

// 관리자 "회원 관리" 화면에서 정지시킬 때 고르는 사유 목록입니다. 프론트
// (frontend/src/pages/Admin/suspensionReasons.js)의 목록과 코드(name)가 반드시 같아야 합니다.
public enum SuspensionReason {
    POLICY_VIOLATION("이용 정책 위반 (욕설·비방·혐오 표현 리뷰)"),
    FRAUDULENT_USE("부정 이용 (매크로/자동화 좌석 선점·예매 반복)"),
    PAYMENT_BOOKING("결제/예매 관련 (상습 노쇼)"),
    CHATBOT_ABUSE("AI 챗봇/시스템 어뷰징 (악의적 프롬프트 반복)"),
    LEGAL_SAFETY("법적/안전 문제 (저작권 침해 게시물)");

    private final String label;

    SuspensionReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SuspensionReason fromCode(String code) {
        return Arrays.stream(values())
                .filter(reason -> reason.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new ApiException("올바르지 않은 정지 사유입니다: " + code, HttpStatus.BAD_REQUEST));
    }
}
