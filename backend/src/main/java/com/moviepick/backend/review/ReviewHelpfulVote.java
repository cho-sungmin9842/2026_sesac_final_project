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

// 리뷰 1건에 대한 "도움이 됐어요" 추천 1건 - review_reports와 대칭되는 긍정적 상호작용입니다.
// (review_id, user_id) 조합이 유니크라 같은 사람이 같은 리뷰를 두 번 추천할 수 없습니다.
@Entity
@Table(name = "review_helpful_votes")
@Getter
public class ReviewHelpfulVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReviewHelpfulVote() {
    }

    public ReviewHelpfulVote(Review review, User user) {
        this.review = review;
        this.user = user;
        this.createdAt = LocalDateTime.now();
    }
}
