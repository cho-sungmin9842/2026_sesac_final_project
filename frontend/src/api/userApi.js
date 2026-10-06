import request from './httpClient'

export function getPreferredGenres(userId) {
  return request('/api/users/me/preferred-genres', { userId })
}

export function updatePreferredGenres(userId, genres) {
  return request('/api/users/me/preferred-genres', { method: 'PUT', userId, body: { genres } })
}

// 관리자 "회원 관리" 화면 - 전체 회원 목록(최근 가입자부터)을 활동 집계(리뷰/예매/찜 수)와 함께 가져옵니다.
export function getAllUsersForAdmin(adminUserId) {
  return request('/api/admin/users', { userId: adminUserId })
}
