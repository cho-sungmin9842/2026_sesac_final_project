// 영화 자체 정보(제목/러닝타임/포스터 등)는 KMDB API로 받아오고, 상영관/회차/좌석 배치는 화면 데모용 목업입니다.
// 다만 어떤 좌석이 이미 예약됐는지는 실제 백엔드(MySQL bookings 테이블)에서 조회합니다 - bookingApi.getReservedSeats 참고.
// 예매 목록 화면(상영중인 영화)은 더 이상 고정 제목 목록이 아니라, movieApi.getNowShowing()이 최근 4주 개봉작을
// KMDB releaseDts~releaseDte로 직접 조회합니다.

export const THEATERS = ['금천점']

export const SHOWTIMES = ['13:00', '16:30', '19:00', '21:40']

export const SEAT_ROWS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']
export const SEATS_PER_ROW = 13

// 연령 구분별 요금표. 주중=월~목, 주말=금~일 및 공휴일(요금표 기준).
export const AGE_CATEGORIES = [
  { key: 'adult', label: '성인', sublabel: '만 19세 이상', weekdayPrice: 14000, weekendPrice: 15000 },
  { key: 'teen', label: '청소년', sublabel: '48개월 이상 ~ 만 18세 이하', weekdayPrice: 11000, weekendPrice: 12000 },
  { key: 'child', label: '어린이', sublabel: '48개월 이상 ~ 만 12세 이하', weekdayPrice: 7000, weekendPrice: 8000 },
  { key: 'senior', label: '우대', sublabel: '만 65세 이상 경로 / 장애인', weekdayPrice: 7000, weekendPrice: 7000 },
]

// 2026년 대한민국 공휴일(대체공휴일 포함). 설날/추석/부처님오신날은 음력 기준이라 매년 날짜가 바뀌므로
// 2026년 기준으로 채워뒀습니다 - 정부 확정 공고와 차이가 있으면 이 목록만 고치면 됩니다.
const HOLIDAYS_2026 = new Set([
  '2026-01-01', // 신정
  '2026-02-16', '2026-02-17', '2026-02-18', // 설날 연휴
  '2026-03-01', '2026-03-02', // 삼일절(일요일) + 대체공휴일
  '2026-05-05', // 어린이날
  '2026-05-24', '2026-05-25', // 부처님오신날(일요일) + 대체공휴일
  '2026-06-06', // 현충일
  '2026-08-15', // 광복절
  '2026-09-24', '2026-09-25', '2026-09-26', // 추석 연휴
  '2026-10-03', // 개천절
  '2026-10-09', // 한글날
  '2026-12-25', // 성탄절
])

// showDate("YYYY-MM-DD")가 주말(금~일) 또는 공휴일이면 true. 예매 화면에서 선택한 상영일자를 기준으로 합니다.
export function isWeekendOrHoliday(dateStr) {
  const day = new Date(`${dateStr}T00:00:00`).getDay() // 0=일 ~ 6=토
  return day === 0 || day === 5 || day === 6 || HOLIDAYS_2026.has(dateStr)
}

export function priceFor(categoryKey, dateStr) {
  const category = AGE_CATEGORIES.find((c) => c.key === categoryKey)
  if (!category) return 0
  return isWeekendOrHoliday(dateStr) ? category.weekendPrice : category.weekdayPrice
}

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
