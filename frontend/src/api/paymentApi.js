import request from './httpClient'

// 토스페이먼츠 결제 승인 + 예매 생성을 한 번에 요청합니다. 결제 승인이 실패하면 예매도 생성되지 않습니다.
export function confirmAndCreateBooking(userId, { paymentKey, orderId, amount, screeningId, seatIds, ticketCounts }) {
  return request('/api/payments/confirm-and-book', {
    method: 'POST',
    userId,
    body: { paymentKey, orderId, amount, screeningId, seatIds, ticketCounts },
  })
}
