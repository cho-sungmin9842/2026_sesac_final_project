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

/**
 * AI 추천 채팅의 "대화방" 하나. ChatGPT 사이드바처럼, 사용자가 "새 대화 시작"을 누른 뒤 메시지를 처음 보낼 때
 * 생성되고(title은 그 첫 메시지를 간단히 줄인 값), 그 뒤로는 이 대화방 안에서만 메시지가 쌓입니다.
 */
@Entity
@Table(name = "chat_conversations")
@Getter
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    protected ChatConversation() {
    }

    public ChatConversation(User user, String title) {
        this.user = user;
        this.title = title;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.lastMessageAt = now;
    }

    // 메시지가 새로 쌓일 때마다 호출해 사이드바 정렬(최근 대화가 위로)에 쓰는 시각을 갱신합니다.
    public void touch() {
        this.lastMessageAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
