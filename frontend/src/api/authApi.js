import request from './httpClient'

export function signup({ nickname, username, password }) {
  return request('/api/auth/signup', { method: 'POST', body: { nickname, username, password } })
}

export function login({ username, password }) {
  return request('/api/auth/login', { method: 'POST', body: { username, password } })
}
