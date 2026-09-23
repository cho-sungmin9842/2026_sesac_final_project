import request from './httpClient'

export function getPreferredGenres(userId) {
  return request('/api/users/me/preferred-genres', { userId })
}

export function updatePreferredGenres(userId, genres) {
  return request('/api/users/me/preferred-genres', { method: 'PUT', userId, body: { genres } })
}
