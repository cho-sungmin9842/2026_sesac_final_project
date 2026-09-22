// 예매/상영관 기능은 아직 백엔드가 없습니다(프로젝트 일정상 KMDB 연동 다음 단계).
// 상영관·회차·좌석 배치는 전부 화면 데모용 목업이고, 영화 자체 정보(제목/러닝타임/포스터 등)만 KMDB API로 받아옵니다.

// 예매 목록 화면(상영중인 영화)에 보여줄 영화들. 실제 상영 스케줄 API가 없어 KMDB에서 제목으로 찾아옵니다.
export const NOW_SHOWING_TITLES = [
  '기생충',
  '범죄도시4',
  '오펜하이머',
  '파묘',
  '서울의 봄',
  '콘크리트 유토피아',
  '듄 파트2',
  '인터스텔라',
]

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

// 상영관·날짜·회차 조합마다 늘 같은 좌석이 예약 완료 상태로 보이도록 결정론적으로 좌석을 고릅니다.
function seededRandom(seed) {
  let value = seed % 2147483647
  if (value <= 0) value += 2147483646
  return () => {
    value = (value * 16807) % 2147483647
    return (value - 1) / 2147483646
  }
}

function hashKey(key) {
  let hash = 0
  for (let i = 0; i < key.length; i += 1) {
    hash = (hash * 31 + key.charCodeAt(i)) % 1000000007
  }
  return hash
}

export function getReservedSeats(key) {
  const random = seededRandom(hashKey(key) || 1)
  const reserved = new Set()
  const totalSeats = SEAT_ROWS.length * SEATS_PER_ROW
  const reservedCount = Math.floor(totalSeats * 0.12)

  while (reserved.size < reservedCount) {
    const row = SEAT_ROWS[Math.floor(random() * SEAT_ROWS.length)]
    const col = Math.floor(random() * SEATS_PER_ROW) + 1
    reserved.add(`${row}${col}`)
  }

  return reserved
}
