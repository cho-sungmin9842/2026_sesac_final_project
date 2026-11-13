package com.moviepick.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moviepick.backend.auth.SuspensionReason;
import com.moviepick.backend.auth.User;

import java.time.LocalDateTime;

/**
 * 관리자 "회원 관리" 화면 전용 - UserDto(로그인/회원가입 응답)와 달리 활동 집계(리뷰/예매/찜 수)와
 * 정지 여부까지 함께 보여줍니다. password_hash는 다른 DTO들과 마찬가지로 절대 포함하지 않습니다
 * (애초에 BCrypt라 복호화도 불가능합니다).
 */
public record UserAdminDto(
        Long id,
        String username,
        String nickname,
        @JsonProperty("isAdmin") boolean admin,
        @JsonProperty("isSuspended") boolean suspended,
        // 정지 중일 때만 값이 있습니다(SuspensionReason의 한국어 라벨) - 목록 화면에서 툴팁 등으로 보여줍니다.
        String suspensionReasonLabel,
        // UserService.ONLINE_THRESHOLD_MINUTES 안에 인증된 요청(X-User-Id)이 감지됐으면 true입니다
        // (UserActivityInterceptor가 lastActiveAt을 갱신합니다).
        @JsonProperty("isOnline") boolean online,
        LocalDateTime createdAt,
        long reviewCount,
        long bookingCount,
        long wishlistCount
) {
    public static UserAdminDto from(User user, long reviewCount, long bookingCount, long wishlistCount, boolean online) {
        String reasonLabel = user.isSuspended() && user.getSuspensionReason() != null
                ? SuspensionReason.fromCode(user.getSuspensionReason()).label()
                : null;
        return new UserAdminDto(
                user.getId(), user.getUsername(), user.getNickname(), user.isAdmin(), user.isSuspended(), reasonLabel,
                online, user.getCreatedAt(), reviewCount, bookingCount, wishlistCount
        );
    }
}
