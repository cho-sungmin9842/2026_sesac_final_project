import { useMemo } from 'react'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
// 한 칸에 영화 제목 칩을 몇 개까지 보여줄지(넘치면 "+N건"으로 줄입니다).
const MAX_CHIPS_PER_DAY = 2
// 좁은 화면에서는 칩 대신 점으로만 표시하는데, 그 점도 너무 많아지지 않게 제한합니다.
const MAX_DOTS_PER_DAY = 4

function pad(value) {
  return String(value).padStart(2, '0')
}

// "YYYY-MM-DD" 키 - booking.showDate("2026-10-21")와 같은 형식이라 문자열 그대로 비교/조회할 수 있습니다
// (Date로 바꿔 쓰면 시간대에 따라 하루씩 밀릴 수 있어서 키 문자열로만 다룹니다).
function dateKey(year, monthIndex, day) {
  return `${year}-${pad(monthIndex + 1)}-${pad(day)}`
}

function todayKey() {
  const now = new Date()
  return dateKey(now.getFullYear(), now.getMonth(), now.getDate())
}

/**
 * 마이페이지 "예매 내역" 탭의 달력 보기 - 상영일 기준으로 예매를 달력에 배치합니다.
 * 달/선택한 날짜 상태는 부모가 들고 있고(아래 목록과 함께 바뀌어야 해서), 여기서는 그리기만 합니다.
 */
function BookingCalendar({ bookings, year, monthIndex, selectedDate, isPast, onSelectDate, onChangeMonth, onGoToday }) {
  const bookingsByDate = useMemo(() => {
    const map = new Map()
    for (const booking of bookings) {
      if (!booking.showDate) continue
      if (!map.has(booking.showDate)) map.set(booking.showDate, [])
      map.get(booking.showDate).push(booking)
    }
    for (const list of map.values()) {
      list.sort((a, b) => String(a.showtime).localeCompare(String(b.showtime)))
    }
    return map
  }, [bookings])

  const firstWeekday = new Date(year, monthIndex, 1).getDay()
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate()
  const today = todayKey()

  // 앞쪽 빈 칸(첫날 요일만큼) + 날짜 칸. 마지막 주는 7칸이 되도록 뒤쪽도 빈 칸으로 채웁니다.
  const cells = []
  for (let i = 0; i < firstWeekday; i += 1) cells.push(null)
  for (let day = 1; day <= daysInMonth; day += 1) cells.push(day)
  while (cells.length % 7 !== 0) cells.push(null)

  return (
    <div className="rounded-lg bg-slate-900 p-3 sm:p-4">
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => onChangeMonth(-1)}
            aria-label="이전 달"
            className="flex h-8 w-8 items-center justify-center rounded-full text-lg text-gray-300 hover:bg-slate-800 hover:text-white"
          >
            ‹
          </button>
          <h3 className="min-w-28 text-center text-sm font-bold text-white">
            {year}년 {monthIndex + 1}월
          </h3>
          <button
            type="button"
            onClick={() => onChangeMonth(1)}
            aria-label="다음 달"
            className="flex h-8 w-8 items-center justify-center rounded-full text-lg text-gray-300 hover:bg-slate-800 hover:text-white"
          >
            ›
          </button>
        </div>
        <button
          type="button"
          onClick={onGoToday}
          className="rounded-md bg-slate-800 px-3 py-1.5 text-xs font-semibold text-gray-300 hover:bg-slate-700"
        >
          오늘
        </button>
      </div>

      <div className="grid grid-cols-7 gap-1">
        {WEEKDAYS.map((weekday, index) => (
          <div
            key={weekday}
            className={`py-1 text-center text-xs font-semibold ${
              index === 0 ? 'text-rose-400' : index === 6 ? 'text-sky-400' : 'text-gray-500'
            }`}
          >
            {weekday}
          </div>
        ))}

        {cells.map((day, index) => {
          if (day === null) return <div key={`empty-${index}`} />

          const key = dateKey(year, monthIndex, day)
          const dayBookings = bookingsByDate.get(key) ?? []
          const isSelected = selectedDate === key
          const isToday = today === key
          const weekday = index % 7
          const hiddenCount = dayBookings.length - MAX_CHIPS_PER_DAY

          return (
            <button
              key={key}
              type="button"
              onClick={() => onSelectDate(isSelected ? null : key)}
              aria-pressed={isSelected}
              aria-label={`${monthIndex + 1}월 ${day}일${dayBookings.length > 0 ? ` 예매 ${dayBookings.length}건` : ''}`}
              className={`flex min-h-14 flex-col items-stretch gap-1 rounded-md border p-1 text-left transition-colors sm:min-h-24 sm:p-1.5 ${
                isSelected ? 'border-indigo-500 bg-indigo-500/10' : 'border-white/5 hover:bg-slate-800'
              }`}
            >
              <span
                className={`flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-semibold ${
                  isToday
                    ? 'border border-gray-400 text-white'
                    : weekday === 0
                      ? 'text-rose-400'
                      : weekday === 6
                        ? 'text-sky-400'
                        : 'text-gray-300'
                }`}
              >
                {day}
              </span>

              {/* 넓은 화면: 상영 시각 + 영화 제목 칩 */}
              <div className="hidden flex-col gap-0.5 sm:flex">
                {dayBookings.slice(0, MAX_CHIPS_PER_DAY).map((booking) => (
                  <span
                    key={booking.id}
                    className={`block truncate rounded px-1.5 py-0.5 text-[10px] font-medium ${
                      isPast(booking) ? 'bg-slate-800 text-gray-500' : 'bg-indigo-500/20 text-indigo-300'
                    }`}
                  >
                    {String(booking.showtime).slice(0, 5)} {booking.movieTitle}
                  </span>
                ))}
                {hiddenCount > 0 && <span className="px-1 text-[10px] text-gray-500">+{hiddenCount}건</span>}
              </div>

              {/* 좁은 화면: 칩이 들어갈 자리가 없어서 점으로만 표시 */}
              {dayBookings.length > 0 && (
                <div className="flex flex-wrap gap-0.5 px-0.5 sm:hidden">
                  {dayBookings.slice(0, MAX_DOTS_PER_DAY).map((booking) => (
                    <span
                      key={booking.id}
                      className={`h-1.5 w-1.5 rounded-full ${isPast(booking) ? 'bg-gray-500' : 'bg-indigo-500'}`}
                    />
                  ))}
                </div>
              )}
            </button>
          )
        })}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500">
        <span className="flex items-center gap-1.5">
          <span className="h-2 w-2 rounded-full bg-indigo-500" /> 상영 예정
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-2 w-2 rounded-full bg-gray-500" /> 상영 종료
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-full border border-gray-400" /> 오늘
        </span>
      </div>
    </div>
  )
}

export default BookingCalendar
