import request from './httpClient'

export function getDownloads(userId) {
  return request('/api/downloads', { userId })
}

export function addDownload(userId, { movieId, movieTitle, posterUrl, sizeMb }) {
  return request('/api/downloads', { method: 'POST', userId, body: { movieId, movieTitle, posterUrl, sizeMb } })
}

export function removeDownload(userId, movieId) {
  return request(`/api/downloads/${encodeURIComponent(movieId)}`, { method: 'DELETE', userId })
}
