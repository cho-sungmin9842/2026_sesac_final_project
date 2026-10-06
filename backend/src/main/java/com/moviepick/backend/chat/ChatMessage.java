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

    // 어느 대화방(ChatConversation) 소속인지. 관계 매핑 대신 Booking.screeningId와 같은 방식으로
    // id만 그대로 들고 있습니다(이 쪽에서 대화방 엔티티 자체를 조회할 일이 없어서 충분합니다).
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "role", nullable = false, length = 10)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 그 메시지가 추천한 영화들의 합성 id("{movieId}_{movieSeq}")를 콤마로 이어붙인 값. 추천이 아니면 null.
    @Column(name = "movie_ids", length = 500)
    private String movieIds;

    // 그 메시지가 "특정 회차 좌석 현황" 답변이면 그 상영(screenings.id), 아니면 null. 화면 재방문 시
    // 이 id로 좌석 상태를 다시 조회해 좌석 배치도를 그대로 복원합니다.
    @Column(name = "screening_id")
    private Long screeningId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(User user, Long conversationId, String role, String content, String movieIds, Long screeningId) {
        this.user = user;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.movieIds = movieIds;
        this.screeningId = screeningId;
        this.createdAt = LocalDateTime.now();
    }
}
