package com.moviepick.backend.chat;

import com.moviepick.backend.chat.dto.ChatMessageDto;
import com.moviepick.backend.chat.dto.ChatRequestDto;
import com.moviepick.backend.chat.dto.ChatResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/history")
    public List<ChatMessageDto> history(@RequestHeader("X-User-Id") Long userId) {
        return chatService.history(userId);
    }

    @PostMapping
    public ChatResponseDto chat(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody ChatRequestDto request) {
        return chatService.reply(userId, request);
    }

    /**
     * "새 대화 시작" - 이 사용자의 저장된 AI 추천 채팅 내역을 전부 지웁니다.
     */
    @DeleteMapping("/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(@RequestHeader("X-User-Id") Long userId) {
        chatService.clearHistory(userId);
    }
}
