package com.moviepick.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 리뷰 사진 첨부 업로드 경로. dir은 상대 경로면 백엔드 실행 디렉터리 기준이고, WebConfig가 이 경로를
// "/uploads/**" URL로 그대로 서빙합니다(ReviewService가 저장한 파일을 그대로 다시 내려주는 용도).
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(String dir) {
}
