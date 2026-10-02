package com.moviepick.backend.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

// "AI 줄거리 요약(스포방지)" 결과를 영구 저장해서, 같은 영화는 서버를 재시작해도 Gemini를 다시 호출하지
// 않도록 합니다(AiSummaryService의 메모리 캐시는 서버가 떠 있는 동안만 유지되는 1차 캐시일 뿐입니다).
@Entity
@Table(name = "ai_summaries")
@Getter
public class AiSummary {

    @Id
    @Column(name = "movie_id", length = 64)
    private String movieId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AiSummary() {
    }

    public AiSummary(String movieId, String summary) {
        this.movieId = movieId;
        this.summary = summary;
        this.createdAt = LocalDateTime.now();
    }
}
