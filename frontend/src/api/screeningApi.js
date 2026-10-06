import request from './httpClient'

// 예매 화면의 상영관/날짜/회차 선택지. 매주 월요일 자정 배치가 생성해둔 이번 주 상영정보 중
// 이 영화(movieId) 것만 날짜/시간순으로 내려줍니다.
export function getScreenings(movieId) {
  return request(`/api/screenings?movieId=${encodeURIComponent(movieId)}`)
}

// 예매 화면/마이페이지 좌석 배치도 다이얼로그가 공통으로 씁니다. 실제 seats 테이블 상태
// (AVAILABLE/BOOKED)를 그대로 내려받습니다. userId를 넘기면(마이페이지 쪽만 넘김) 각 좌석에
// bookedByMe(내가 예매한 좌석인지)도 함께 내려옵니다.
export function getScreeningSeats(screeningId, userId) {
  return request(`/api/screenings/${screeningId}/seats`, userId != null ? { userId } : {})
}
