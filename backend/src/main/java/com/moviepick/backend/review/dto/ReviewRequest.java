package com.moviepick.backend.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
        @NotBlank(message = "영화 제목이 필요합니다.") String movieTitle,
        @NotNull(message = "평점을 선택해주세요.") @Min(1) @Max(5) Integer score,
        @NotBlank(message = "리뷰 내용을 입력해주세요.") String content
) {
}
