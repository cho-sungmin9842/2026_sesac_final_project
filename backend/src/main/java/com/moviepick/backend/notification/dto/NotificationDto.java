package com.moviepick.backend.notification.dto;

import com.moviepick.backend.notification.Notification;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String message,
        Long bookingId,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getMessage(),
                notification.getBookingId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
