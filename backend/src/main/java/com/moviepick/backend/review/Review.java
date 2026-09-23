package com.moviepick.backend.review;

import com.moviepick.backend.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Getter
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movie_id", nullable = false, length = 64)
    private String movieId;

    // 마이페이지 "내가 쓴 리뷰" 탭에서 영화 제목을 보여주려고 저장 시점에 함께 저장해둡니다
    // (downloads/wishlists/watched_movies와 동일한 비정규화 패턴 - 영화별 KMDB 조회를 리뷰마다 반복하지 않기 위함).
    @Column(name = "movie_title", nullable = false, length = 255)
    private String movieTitle;

    // 리뷰 목록을 DTO로 바꿀 때 매번 트랜잭션을 열지 않아도 되도록 EAGER로 가져옵니다(리뷰 테이블 규모가 작아 문제 없음).
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Review() {
    }

    public Review(String movieId, String movieTitle, User user, Integer score, String content) {
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.user = user;
        this.score = score;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public void update(Integer score, String content) {
        this.score = score;
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }
}
