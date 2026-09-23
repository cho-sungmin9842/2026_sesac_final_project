import request from './httpClient'

export function getWatched(userId) {
  return request('/api/watched', { userId })
}

export function isWatched(userId, movieId) {
  return request(`/api/watched/${encodeURIComponent(movieId)}`, { userId })
}

export function addWatched(userId, { movieId, movieTitle, posterUrl }) {
  return request('/api/watched', { method: 'POST', userId, body: { movieId, movieTitle, posterUrl } })
}

export function removeWatched(userId, movieId) {
  return request(`/api/watched/${encodeURIComponent(movieId)}`, { method: 'DELETE', userId })
}
