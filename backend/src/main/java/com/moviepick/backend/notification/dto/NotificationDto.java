package com.moviepick.backend.notification.dto;

import com.moviepick.backend.notification.Notification;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String message,
        Long bookingId,
        Long reviewReportId,
        Long reviewId,
        String movieId,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getMessage(),
                notification.getBookingId(),
                notification.getReviewReportId(),
                notification.getReviewId(),
                notification.getMovieId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
