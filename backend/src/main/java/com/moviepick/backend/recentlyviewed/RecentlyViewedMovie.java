package com.moviepick.backend.recentlyviewed;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "recently_viewed_movies", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"}))
@Getter
public class RecentlyViewedMovie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "movie_id", nullable = false, length = 64)
    private String movieId;

    @Column(name = "movie_title", nullable = false, length = 255)
    private String movieTitle;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    protected RecentlyViewedMovie() {
    }

    public RecentlyViewedMovie(User user, String movieId, String movieTitle, String posterUrl) {
        this.user = user;
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.posterUrl = posterUrl;
        this.viewedAt = LocalDateTime.now();
    }

    // 같은 영화를 또 봤을 때 기존 행을 지우고 새로 만드는 대신 시간/제목/포스터만 갱신합니다(1인당 1행 유지).
    public void touch(String movieTitle, String posterUrl) {
        this.movieTitle = movieTitle;
        this.posterUrl = posterUrl;
        this.viewedAt = LocalDateTime.now();
    }
}
