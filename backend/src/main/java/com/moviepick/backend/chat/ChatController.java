package com.moviepick.backend.chat;

import com.moviepick.backend.chat.dto.ChatConversationDto;
import com.moviepick.backend.chat.dto.ChatMessageDto;
import com.moviepick.backend.chat.dto.ChatRequestDto;
import com.moviepick.backend.chat.dto.ChatResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * AI 추천 사이드바 - 이 사용자의 대화방 목록(최근 대화가 위로)입니다.
     */
    @GetMapping("/conversations")
    public List<ChatConversationDto> conversations(@RequestHeader("X-User-Id") Long userId) {
        return chatService.listConversations(userId);
    }

    /**
     * 사이드바에서 과거 대화방을 클릭했을 때 그 대화방의 메시지 전체를 시간순으로 가져옵니다.
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessageDto> conversationMessages(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long conversationId
    ) {
        return chatService.historyForConversation(userId, conversationId);
    }

    /**
     * AI 추천 채팅. request.conversationId()가 없으면("새 대화 시작" 뒤 첫 메시지) 대화방을 새로 만들고,
     * 있으면 그 대화방에 이어붙입니다. 응답의 conversationId로 화면이 이번에 쓰인(또는 새로 만들어진)
     * 대화방을 알 수 있습니다.
     */
    @PostMapping
    public ChatResponseDto chat(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody ChatRequestDto request) {
        return chatService.reply(userId, request);
    }

    /**
     * 사이드바에서 대화방을 우클릭 → 삭제했을 때 - 그 대화방과 메시지를 전부 지웁니다.
     */
    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@RequestHeader("X-User-Id") Long userId, @PathVariable Long conversationId) {
        chatService.deleteConversation(userId, conversationId);
    }
}
