package com.moviepick.backend.auth.dto;

// user는 자동 로그인을 위해(프론트가 그대로 로그인 상태로 전환), tempPassword는 혹시 로그아웃된 뒤 다시
// 로그인해야 할 때를 대비해 화면에 한 번 보여주기 위해 함께 내려줍니다. 사용자는 로그인 직후 마이페이지에서
// 원하는 비밀번호로 바꿀 수 있습니다.
public record ResetPasswordResponse(UserDto user, String tempPassword) {
}
