package com.moviepick.backend.notification;

import com.moviepick.backend.notification.dto.NotificationDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDto> list(@RequestHeader("X-User-Id") Long userId) {
        return notificationService.list(userId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@RequestHeader("X-User-Id") Long userId) {
        return Map.of("count", notificationService.countUnread(userId));
    }

    @PostMapping("/{id}/read")
    public void markRead(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        notificationService.markRead(userId, id);
    }

    @PostMapping("/read-all")
    public void markAllRead(@RequestHeader("X-User-Id") Long userId) {
        notificationService.markAllRead(userId);
    }
}
