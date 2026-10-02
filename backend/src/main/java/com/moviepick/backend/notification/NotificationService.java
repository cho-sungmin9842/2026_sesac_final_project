package com.moviepick.backend.notification;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.notification.dto.NotificationDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // 예매가 완료되면 예매한 본인에게 1건, 관리자 전원에게 각 1건씩 알림을 생성합니다.
    public void notifyBookingCompleted(User bookingUser, Long bookingId, String summary) {
        notificationRepository.save(new Notification(bookingUser, "예매가 완료되었습니다 - " + summary, bookingId));

        for (User admin : userRepository.findByAdminTrue()) {
            // 관리자 본인이 직접 예매한 경우까지 스스로에게 "누가 예매했다"는 중복 알림을 보낼 필요는 없습니다.
            if (admin.getId().equals(bookingUser.getId())) {
                continue;
            }
            notificationRepository.save(
                    new Notification(admin, bookingUser.getNickname() + "님의 예매 - " + summary, bookingId)
            );
        }
    }

    public List<NotificationDto> list(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDto::from)
                .toList();
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException("알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ApiException("본인의 알림만 읽음 처리할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        notification.markRead();
        notificationRepository.save(notification);
    }

    public void markAllRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(notification -> !notification.isRead())
                .toList();
        unread.forEach(Notification::markRead);
        notificationRepository.saveAll(unread);
    }
}
