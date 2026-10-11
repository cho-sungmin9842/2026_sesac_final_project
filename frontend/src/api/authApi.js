import request from './httpClient'

export function signup({ nickname, username, password }) {
  return request('/api/auth/signup', { method: 'POST', body: { nickname, username, password } })
}

export function login({ username, password }) {
  return request('/api/auth/login', { method: 'POST', body: { username, password } })
}

// 사용자가 새 비밀번호를 직접 정하지 않고, 서버가 발급한 임시 비밀번호로 응답(user + tempPassword)을 받습니다.
export function resetPassword({ username, nickname }) {
  return request('/api/auth/reset-password', { method: 'POST', body: { username, nickname } })
}
