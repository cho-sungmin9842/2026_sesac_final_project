package com.moviepick.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // created_at은 초 단위(DATETIME)라 같은 턴의 user/ai 메시지가 같은 초에 저장되면 동점이 나고,
    // 그 경우 MySQL이 저장/삽입 순서를 보장해주지 않아 AI 답변이 그 답변을 유발한 사용자 메시지보다
    // 앞서 조회되는 문제가 있었습니다(2026-10-13 실측 - 화면에 AI 답변이 사용자 메시지보다 먼저 보임).
    // id가 auto_increment라 삽입 순서를 항상 정확히 반영하므로, 동점일 때 id를 2차 정렬 기준으로 써서
    // 순서를 결정적으로 고정합니다.
    List<ChatMessage> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);
}
