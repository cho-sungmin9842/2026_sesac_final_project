import request from './httpClient'

export function getWishlist(userId) {
  return request('/api/wishlist', { userId })
}

export function isWishlisted(userId, movieId) {
  return request(`/api/wishlist/${encodeURIComponent(movieId)}`, { userId })
}

export function addWishlist(userId, { movieId, movieTitle, posterUrl }) {
  return request('/api/wishlist', { method: 'POST', userId, body: { movieId, movieTitle, posterUrl } })
}

export function removeWishlist(userId, movieId) {
  return request(`/api/wishlist/${encodeURIComponent(movieId)}`, { method: 'DELETE', userId })
}
