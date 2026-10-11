import request from './httpClient'

export function getPreferredGenres(userId) {
  return request('/api/users/me/preferred-genres', { userId })
}

export function updatePreferredGenres(userId, genres) {
  return request('/api/users/me/preferred-genres', { method: 'PUT', userId, body: { genres } })
}

// 마이페이지 "비밀번호 변경" - 비밀번호 찾기로 받은 임시 비밀번호를 원하는 비밀번호로 바꿀 때도 씁니다.
export function changePassword(userId, { currentPassword, newPassword }) {
  return request('/api/users/me/password', { method: 'PUT', userId, body: { currentPassword, newPassword } })
}

// 마이페이지 "회원 탈퇴" - 비밀번호 확인 후 계정과 관련 데이터를 전부 삭제합니다.
export function deleteAccount(userId, password) {
  return request('/api/users/me', { method: 'DELETE', userId, body: { password } })
}

// 관리자 "회원 관리" 화면 - 전체 회원 목록(최근 가입자부터)을 활동 집계(리뷰/예매/찜 수)와 함께 가져옵니다.
export function getAllUsersForAdmin(adminUserId) {
  return request('/api/admin/users', { userId: adminUserId })
}
