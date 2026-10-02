import request from './httpClient'

// 예매 화면의 상영관/날짜/회차 선택지. 매주 월요일 자정 배치가 생성해둔 이번 주 상영정보 중
// 이 영화(movieId) 것만 날짜/시간순으로 내려줍니다.
export function getScreenings(movieId) {
  return request(`/api/screenings?movieId=${encodeURIComponent(movieId)}`)
}

// 예매 화면의 좌석 배치도. 실제 seats 테이블 상태(AVAILABLE/BOOKED)를 그대로 내려받습니다.
export function getScreeningSeats(screeningId) {
  return request(`/api/screenings/${screeningId}/seats`)
}
