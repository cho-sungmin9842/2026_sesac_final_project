import request from './httpClient'

/**
 * AI 추천 사이드바 - 이 사용자의 대화방 목록을 최근 대화가 위로 오도록 가져옵니다.
 * 항목은 { id, title, createdAt, lastMessageAt } 형태입니다.
 */
export function getConversations(userId) {
  return request('/api/chat/conversations', { userId })
}

/**
 * 사이드바에서 과거 대화방을 클릭했을 때, 그 대화방의 메시지 전체를 시간순으로 가져옵니다.
 * 항목은 { role: "user" | "ai", content, movies } 형태입니다(movies는 추천 메시지가 아니면 빈 배열).
 */
export function getConversationMessages(userId, conversationId) {
  return request(`/api/chat/conversations/${conversationId}/messages`, { userId })
}

/**
 * AI 추천 채팅. conversationId가 없으면("새 대화 시작" 뒤 첫 메시지) 서버가 대화방을 새로 만들어
 * 응답의 conversationId로 알려줍니다. 응답은 { conversationId, reply, movies } 형태입니다
 * (movies는 KMDB에서 실제로 찾은 후보만 옵니다. 없으면 빈 배열).
 */
export function sendChatMessage(userId, conversationId, message) {
  return request('/api/chat', { method: 'POST', userId, body: { message, conversationId } })
}

/**
 * 사이드바에서 대화방을 우클릭 → 삭제했을 때 - 그 대화방과 메시지를 전부 지웁니다.
 */
export function deleteConversation(userId, conversationId) {
  return request(`/api/chat/conversations/${conversationId}`, { method: 'DELETE', userId })
}
