import request from './httpClient'

// 로그인된 상태에서 주기적으로 호출해, 그 사이 관리자가 정지시키지 않았는지 확인합니다(AuthContext.jsx).
// 정지됐으면 403 에러(error.message에 안내 문구)로 응답합니다.
export function checkSession(userId) {
  return request('/api/users/me', { userId })
}

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

// 관리자 "회원 관리" 화면 - 회원을 정지하거나(suspended: true, reason 필수 - suspensionReasons.js의
// key 중 하나) 정지를 해제합니다(false, reason 불필요). 정지된 회원은 로그인만 막히고 데이터는 그대로
// 남습니다.
export function suspendUser(adminUserId, targetUserId, suspended, reason = null) {
  return request(`/api/admin/users/${targetUserId}/suspend`, {
    method: 'PATCH',
    userId: adminUserId,
    body: { suspended, reason },
  })
}

// 관리자 "회원 관리" 화면 - 비밀번호 확인 없이 관리자가 직접 회원을 삭제합니다(본인 탈퇴와 달리
// 비밀번호를 모르는 다른 사람의 계정을 지우는 것이라 확인 절차가 다릅니다).
export function deleteUserForAdmin(adminUserId, targetUserId) {
  return request(`/api/admin/users/${targetUserId}`, { method: 'DELETE', userId: adminUserId })
}
