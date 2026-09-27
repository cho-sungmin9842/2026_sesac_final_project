package com.moviepick.backend.chat;

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
@Table(name = "chat_messages")
@Getter
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role", nullable = false, length = 10)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 그 메시지가 추천한 영화들의 합성 id("{movieId}_{movieSeq}")를 콤마로 이어붙인 값. 추천이 아니면 null.
    @Column(name = "movie_ids", length = 500)
    private String movieIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(User user, String role, String content, String movieIds) {
        this.user = user;
        this.role = role;
        this.content = content;
        this.movieIds = movieIds;
        this.createdAt = LocalDateTime.now();
    }
}
