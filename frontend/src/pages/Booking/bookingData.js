// 영화 자체 정보(제목/러닝타임/포스터 등)는 KMDB API로 받아오고, 상영관/회차/좌석 배치는 화면 데모용 목업입니다.
// 다만 어떤 좌석이 이미 예약됐는지는 실제 백엔드(MySQL bookings 테이블)에서 조회합니다 - bookingApi.getReservedSeats 참고.
// 예매 목록 화면(상영중인 영화)은 더 이상 고정 제목 목록이 아니라, movieApi.getNowShowing()이 최근 2개월 개봉작을
// KMDB releaseDts~releaseDte로 직접 조회합니다.

export const THEATERS = ['강남점', '홍대점', '잠실점']

export const SHOWTIMES = ['13:00', '16:30', '19:00', '21:40']

export const PRICE_PER_SEAT = 12000

export const SEAT_ROWS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']
export const SEATS_PER_ROW = 13

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

// 오늘부터 4일치 날짜를 만듭니다.
export function getBookingDates() {
  return Array.from({ length: 4 }, (_, i) => {
    const date = new Date()
    date.setDate(date.getDate() + i)
    return {
      value: date.toISOString().slice(0, 10),
      label: `${date.getMonth() + 1}/${date.getDate()}(${WEEKDAYS[date.getDay()]})`,
    }
  })
}
