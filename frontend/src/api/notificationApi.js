import request from './httpClient'

export function getNotifications(userId) {
  return request('/api/notifications', { userId })
}

export function getUnreadCount(userId) {
  return request('/api/notifications/unread-count', { userId })
}

export function markNotificationRead(userId, id) {
  return request(`/api/notifications/${id}/read`, { method: 'POST', userId })
}

export function markAllNotificationsRead(userId) {
  return request('/api/notifications/read-all', { method: 'POST', userId })
}
