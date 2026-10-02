import request from './httpClient'

// 마이페이지 "예매 내역" 탭 - 최신 예매부터 내려받습니다.
export function getMyBookings(userId) {
  return request('/api/bookings', { userId })
}

// screeningId + 좌석 id 목록으로 예매를 생성합니다. 상영관/날짜/시간/영화 정보는 서버가 screeningId로
// screenings를 조회해서 채우므로 여기서 따로 보내지 않습니다.
export function createBooking(userId, { screeningId, seatIds, ticketCounts }) {
  return request('/api/bookings', {
    method: 'POST',
    userId,
    body: { screeningId, seatIds, ticketCounts },
  })
}
