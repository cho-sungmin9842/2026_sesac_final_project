import request from './httpClient'

// 예매 화면의 상영관/날짜/회차 선택지. 매주 월요일 자정 배치가 생성해둔 이번 주 상영정보 중
// 이 영화(movieId) 것만 날짜/시간순으로 내려줍니다.
export function getScreenings(movieId) {
  return request(`/api/screenings?movieId=${encodeURIComponent(movieId)}`)
}

// 예매 화면/마이페이지 좌석 배치도 다이얼로그가 공통으로 씁니다. 실제 seats 테이블 상태
// (AVAILABLE/HELD/BOOKED - HELD는 다른 사용자가 선택 중이라 임시로 묶인 좌석)를 그대로 내려받습니다.
// userId를 넘기면 각 좌석에 bookedByMe(내가 예매한 좌석인지)/heldByMe(내가 지금 선점 중인지)도
// 함께 내려옵니다.
export function getScreeningSeats(screeningId, userId) {
  return request(`/api/screenings/${screeningId}/seats`, userId != null ? { userId } : {})
}

// 좌석을 선택(클릭)하는 순간 서버에 임시로 선점(TTL 5분)을 겁니다 - 다른 사용자 화면에는 바로
// "선택 중"으로 보이고 클릭도 막혀서, 결제 직전에야 충돌이 드러나던 문제를 막습니다. 이미 예약됐거나
// 다른 사용자가 선점 중이면 에러를 던집니다(409).
export function holdSeat(screeningId, seatId, userId) {
  return request(`/api/screenings/${screeningId}/seats/${seatId}/hold`, { method: 'POST', userId })
}

// 좌석 선택을 해제하거나 화면을 벗어날 때 호출하는 best-effort 해제입니다 - 내가 선점한 좌석이
// 아니면(이미 해제/만료/다른 사람 것) 서버가 조용히 아무 일도 하지 않습니다.
export function releaseSeatHold(screeningId, seatId, userId) {
  return request(`/api/screenings/${screeningId}/seats/${seatId}/hold`, { method: 'DELETE', userId })
}
