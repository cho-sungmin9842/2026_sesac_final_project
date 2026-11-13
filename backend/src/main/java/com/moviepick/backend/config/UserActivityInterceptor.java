package com.moviepick.backend.config;

import com.moviepick.backend.auth.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// 관리자 "회원 관리" 화면의 온라인/오프라인 표시용 - 로그인된 사용자가 보내는 모든 인증된 API 요청
// (X-User-Id 헤더가 실린 요청)마다 그 사용자의 최근 활동 시각을 갱신합니다. 별도의 하트비트 API
// 없이, 이미 오가는 요청(알림 폴링 등)만으로 "지금 접속 중인지"를 판단할 수 있습니다. 헤더가 없거나
// 숫자가 아니거나 갱신이 실패해도(탈퇴한 사용자 등) 본 요청 자체는 그대로 진행시킵니다 - 접속 상태
// 갱신은 부가 기능이라 이것 때문에 정상 요청을 막을 이유가 없습니다.
public class UserActivityInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public UserActivityInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("X-User-Id");
        if (header != null) {
            try {
                userService.touchActivity(Long.parseLong(header));
            } catch (Exception ignored) {
                // 접속 상태 갱신 실패는 조용히 무시합니다(위 주석 참고).
            }
        }
        return true;
    }
}
