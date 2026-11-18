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

// 아이디 찾기 - 닉네임+비밀번호가 일치하면 아이디를 알려줍니다(resetPassword의 반대 방향).
export function findUsername({ nickname, password }) {
  return request('/api/auth/find-username', { method: 'POST', body: { nickname, password } })
}

// 구글 로그인 콜백(OAuthCompletePage)이 리다이렉트로 받은 1회성 토큰을 실제 로그인된
// 사용자 정보로 바꿉니다. 토큰이 유효하지 않거나 만료됐으면(60초) 에러가 납니다.
export function exchangeOAuthToken(token) {
  return request('/api/auth/oauth/exchange', { method: 'POST', body: { token } })
}
