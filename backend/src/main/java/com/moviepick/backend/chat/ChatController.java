package com.moviepick.backend.chat;

import com.moviepick.backend.chat.dto.ChatMessageDto;
import com.moviepick.backend.chat.dto.ChatRequestDto;
import com.moviepick.backend.chat.dto.ChatResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
