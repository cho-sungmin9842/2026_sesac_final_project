import request from './httpClient'

export function signup({ nickname, username, password }) {
  return request('/api/auth/signup', { method: 'POST', body: { nickname, username, password } })
}

export function login({ username, password }) {
  return request('/api/auth/login', { method: 'POST', body: { username, password } })
}

export function resetPassword({ username, nickname, newPassword }) {
  return request('/api/auth/reset-password', { method: 'POST', body: { username, nickname, newPassword } })
}
