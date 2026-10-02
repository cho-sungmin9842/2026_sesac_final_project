// 영화 자체 정보(제목/러닝타임/포스터 등)는 KMDB API로 받아오고, 상영관/날짜/회차/좌석 배치는 전부 실제
// screenings/seats 테이블(screeningApi.getScreenings/getScreeningSeats)에서 조회합니다. 여기 남은 건
// 연령 구분별 요금표·공휴일 판정처럼 "서버가 결제 금액을 계산할 때 쓰는 규칙"뿐입니다.

// 연령 구분별 요금표. 시간대(조조/일반/심야) x 주중(월~목)/주말(금~일)·공휴일로 요금이 달라집니다.
// 우대석은 시간대와 무관하게 항상 동일한 요금입니다.
// 백엔드 TicketPricing.java와 값을 맞춰뒀습니다 - 가격을 바꿀 때는 두 군데 다 고쳐야 합니다.
export const AGE_CATEGORIES = [
  {
    key: 'adult',
    label: '성인',
    sublabel: '만 19세 이상',
    prices: {
      morning: { weekday: 11000, weekend: 12000 },
      normal: { weekday: 14000, weekend: 15000 },
      lateNight: { weekday: 13000, weekend: 14000 },
    },
  },
  {
    key: 'teen',
    label: '청소년',
    sublabel: '만 12세 ~ 만 18세',
    prices: {
      morning: { weekday: 8000, weekend: 9000 },
      normal: { weekday: 11000, weekend: 12000 },
      lateNight: { weekday: 10000, weekend: 11000 },
    },
  },
  {
    key: 'child',
    label: '어린이',
    sublabel: '만 5세 ~ 만 11세',
    prices: {
      morning: { weekday: 5000, weekend: 6000 },
      normal: { weekday: 7000, weekend: 8000 },
      lateNight: { weekday: 6000, weekend: 7000 },
    },
  },
  {
    key: 'senior',
    label: '우대',
    sublabel: '만 65세 이상 경로 / 장애인',
    prices: {
      morning: { weekday: 7000, weekend: 7000 },
      normal: { weekday: 7000, weekend: 7000 },
      lateNight: { weekday: 7000, weekend: 7000 },
    },
  },
]

export const TIME_PERIOD_LABELS = { morning: '조조', normal: '일반', lateNight: '심야' }

// showtime("HH:mm")의 시작 시각 기준 - 08:00~10:00=조조, 10:00~21:00=일반, 21:00~23:00=심야.
export function timePeriodFor(showtime) {
  if (!showtime) return 'normal'
  const hour = Number(showtime.slice(0, showtime.indexOf(':')))
  if (hour < 10) return 'morning'
  if (hour < 21) return 'normal'
  return 'lateNight'
}

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

export function priceFor(categoryKey, dateStr, showtime) {
  const category = AGE_CATEGORIES.find((c) => c.key === categoryKey)
  if (!category) return 0
  const tier = category.prices[timePeriodFor(showtime)]
  return isWeekendOrHoliday(dateStr) ? tier.weekend : tier.weekday
}

// 영화 관람가 등급(movie.ageRating)에 따라 선택할 수 없는 인원 구분(ticketCategory) 목록을 돌려줍니다.
// KMDB 전체 카탈로그(53,142건)를 실제로 스캔해서 확인한 rating 표기 23종을 기준으로 신/구 표기를 전부
// 포함시켰습니다. 백엔드 AgeRatingPolicy.java와 로직/값을 맞춰야 합니다.
export function getDisallowedCategories(ageRating) {
  if (!ageRating) return []
  // 어린이·청소년 모두 관람 불가(사실상 성인만 가능) 등급 - 신/구 표기 전부 포함. "제한상영가"는 19금보다도
  // 엄격한 등급이라 같은 급으로 묶습니다.
  if (
    ageRating.includes('청소년관람불가') ||
    ageRating.includes('미성년자관람불가') ||
    ageRating.includes('연소자불가') ||
    ageRating.includes('제한상영가') ||
    ageRating.includes('19세')
  ) {
    return ['child', 'teen']
  }
  // 12세/15세 이상 등급(구 표기 중학생/고등학생/국민학생 포함) - 청소년(만 12~18세)은 그대로 두고 어린이만 막습니다.
  if (
    ageRating.includes('12세') ||
    ageRating.includes('15세') ||
    ageRating.includes('중학생') ||
    ageRating.includes('고등학생') ||
    ageRating.includes('국민학생관람불가')
  ) {
    return ['child']
  }
  // "전체관람가", "모두관람가", "연소자관람가"/"미성년자관람가"/"국민학생이상관람가"(구 표기 - 전부 관람
  // 허용 쪽) 등은 제한 없음.
  return []
}
