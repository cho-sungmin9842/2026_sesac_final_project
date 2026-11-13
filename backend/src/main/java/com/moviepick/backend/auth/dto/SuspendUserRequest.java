package com.moviepick.backend.auth.dto;

// 관리자 "회원 관리" 화면의 정지/정지 해제 토글 요청 본문. reason은 suspended=true일 때만 필요합니다
// (SuspensionReason의 name() 중 하나 - 정지 해제 시에는 비워서 보내면 됩니다).
public record SuspendUserRequest(boolean suspended, String reason) {
}
