import request from './httpClient'

/**
 * 저장된 AI 추천 채팅 내역을 시간순으로 가져옵니다.
 * 항목은 { role: "user" | "ai", content, movies } 형태입니다(movies는 추천 메시지가 아니면 빈 배열).
 */
export function getChatHistory(userId) {
  return request('/api/chat/history', { userId })
}

/**
 * AI 추천 채팅. 대화 내역은 서버(DB)가 사용자별로 들고 있으므로 이번 메시지만 보내면 됩니다.
 * 응답은 { reply, movies } 형태입니다(movies는 KMDB에서 실제로 찾은 후보만 옵니다. 없으면 빈 배열).
 */
export function sendChatMessage(userId, message) {
  return request('/api/chat', { method: 'POST', userId, body: { message } })
}
