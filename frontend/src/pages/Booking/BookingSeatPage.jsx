import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { getMovieDetail } from '../../api/movieApi'
import { getScreenings, getScreeningSeats } from '../../api/screeningApi'
import { createBooking } from '../../api/bookingApi'
import PosterPlaceholder from '../../components/common/PosterPlaceholder'
import SeatMap from './components/SeatMap'
import TossPaymentWidget from './components/TossPaymentWidget'
import {
  AGE_CATEGORIES,
  TIME_PERIOD_LABELS,
  getDisallowedCategories,
  isWeekendOrHoliday,
  priceFor,
  timePeriodFor,
} from './bookingData'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function formatDateLabel(dateStr) {
  const date = new Date(`${dateStr}T00:00:00`)
  return `${date.getMonth() + 1}/${date.getDate()}(${WEEKDAYS[date.getDay()]})`
}

// 백엔드는 상영 시각을 "HH:mm:ss"로 내려주는데, 화면에는 "HH:mm"만 보여줍니다.
function toHourMinute(timeStr) {
  return timeStr.slice(0, 5)
}

// 상영 시작 30분 전까지만 예매 가능 - 날짜만 비교하면 "오늘 이미 지난 회차"가 걸러지지 않으므로
// 날짜+시간을 합쳐서 접속 시점과 비교합니다(백엔드 BookingService의 같은 기준과 맞춰야 합니다).
const BOOKING_CUTOFF_MINUTES = 30

function isBookable(screening) {
  const startDateTime = new Date(`${screening.date}T${screening.startTime}`)
  const cutoff = new Date(Date.now() + BOOKING_CUTOFF_MINUTES * 60 * 1000)
  return startDateTime >= cutoff
}

function PillButton({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-lg px-4 py-2 text-sm font-semibold ${
        active ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
      }`}
    >
      {children}
    </button>
  )
}

function BookingSeatPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const [movie, setMovie] = useState(null)
  // 실제 상영정보(매주 월요일 자정 배치가 생성해둔 이번 주 분량) - 지난 회차는 예매 대상이 아니라 미리 거릅니다.
  const [screenings, setScreenings] = useState([])
  const [status, setStatus] = useState('loading')
  const [theater, setTheater] = useState('')
  const [showDate, setShowDate] = useState('')
  const [showtime, setShowtime] = useState('')
  const [seats, setSeats] = useState([])
  const [selectedSeats, setSelectedSeats] = useState([])
  const [ticketCounts, setTicketCounts] = useState({ adult: 0, teen: 0, child: 0, senior: 0 })
  const [showPaymentWidget, setShowPaymentWidget] = useState(false)

  useEffect(() => {
    let cancelled = false
    setStatus('loading')
    Promise.all([getMovieDetail(id), getScreenings(id)])
      .then(([movieDto, screeningList]) => {
        if (cancelled) return
        const upcoming = screeningList.filter(isBookable)
        const firstTheater = [...new Set(upcoming.map((s) => s.theaterName))].sort()[0] ?? ''
        const firstDate = [...new Set(upcoming.map((s) => s.date))].sort()[0] ?? ''
        setMovie(movieDto)
        setScreenings(upcoming)
        setTheater(firstTheater)
        setShowDate(firstDate)
        setStatus('ready')
      })
      .catch(() => {
        if (cancelled) return
        setStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [id])

  const theaterOptions = [...new Set(screenings.map((s) => s.theaterName))].sort()
  // 날짜 선택지는 "이 영화의 아무 상영관"이 아니라 "지금 고른 상영관"에 실제로 회차가 있는 날짜만 보여줍니다
  // (그래야 상영관+날짜 조합에 회차가 없는 상태 자체가 애초에 선택되지 않습니다).
  const dateOptions = [...new Set(screenings.filter((s) => s.theaterName === theater).map((s) => s.date))]
    .sort()
    .map((value) => ({ value, label: formatDateLabel(value) }))
  const showtimeOptions = screenings
    .filter((s) => s.theaterName === theater && s.date === showDate)
    .map((s) => toHourMinute(s.startTime))
    .sort()
  // 지금 고른 상영관/날짜/회차에 정확히 맞는 상영정보 1건 - 좌석 조회/예매 생성은 전부 이 id로 합니다.
  const currentScreening = screenings.find(
    (s) => s.theaterName === theater && s.date === showDate && toHourMinute(s.startTime) === showtime,
  )
  const screeningId = currentScreening?.id ?? null

  // 상영관을 바꿔서 지금 고른 날짜가 그 상영관에 더 이상 없으면, 그 상영관의 첫 날짜로 다시 맞춥니다.
  useEffect(() => {
    if (dateOptions.length === 0) {
      if (showDate !== '') setShowDate('')
      return
    }
    if (!dateOptions.some((date) => date.value === showDate)) {
      setShowDate(dateOptions[0].value)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [theater, screenings])

  // 상영관/날짜를 바꿔서 지금 고른 회차가 그 조합에 더 이상 없으면, 그 조합의 첫 회차로 다시 맞춥니다.
  useEffect(() => {
    if (showtimeOptions.length === 0) {
      if (showtime !== '') setShowtime('')
      return
    }
    if (!showtimeOptions.includes(showtime)) {
      setShowtime(showtimeOptions[0])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [theater, showDate, screenings])

  const refreshSeats = () => {
    if (!screeningId) return
    getScreeningSeats(screeningId).then(setSeats)
  }

  useEffect(() => {
    if (!screeningId) {
      setSeats([])
      return
    }
    refreshSeats()
    // 상영관/날짜/회차가 바뀌면 좌석 배치도 달라지니 선택과 결제위젯은 초기화합니다.
    setSelectedSeats([])
    setShowPaymentWidget(false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screeningId])

  const toggleSeat = (seat) => {
    const isCurrentlySelected = selectedSeats.includes(seat.id)
    if (!isCurrentlySelected && seat.seatType === 'WHEELCHAIR') {
      const confirmed = window.confirm('장애인석을 선택했습니다.\n장애인석을 선택하시겠습니까?')
      if (!confirmed) return
    }
    setSelectedSeats((prev) =>
      prev.includes(seat.id) ? prev.filter((seatId) => seatId !== seat.id) : [...prev, seat.id],
    )
  }

  const updateTicketCount = (key, delta) => {
    if (disallowedCategories.includes(key)) return
    setTicketCounts((prev) => ({ ...prev, [key]: Math.max(0, prev[key] + delta) }))
  }

  const hasScreenings = screenings.length > 0
  // 관람가 등급(예: 12세이상관람가)에 맞지 않는 인원 구분은 아예 선택할 수 없도록 막습니다.
  const disallowedCategories = status === 'ready' ? getDisallowedCategories(movie.ageRating) : []
  const holidayPricing = showDate ? isWeekendOrHoliday(showDate) : false
  const timePeriod = timePeriodFor(showtime)
  const totalTickets = Object.values(ticketCounts).reduce((sum, count) => sum + count, 0)
  const totalPrice = AGE_CATEGORIES.reduce(
    (sum, category) => sum + ticketCounts[category.key] * priceFor(category.key, showDate, showtime),
    0,
  )
  // 좌석은 인원수만큼 정확히 골라야 결제할 수 있습니다(누가 어느 좌석인지까지는 구분하지 않습니다).
  const seatsMatchTickets = totalTickets > 0 && selectedSeats.length === totalTickets
  const selectedSeatLabels = selectedSeats
    .map((seatId) => seats.find((seat) => seat.id === seatId))
    .filter(Boolean)
    .map((seat) => `${seat.rowLabel}${seat.colNo}`)

  // 결제위젯의 "결제하기"는 토스 실제 결제창으로 넘어가지 않고, 이 자리에서 바로 예매를 생성합니다
  // (결제수단 선택 UI는 테스트/데모용으로 그대로 보여주되, 실제 결제 승인 절차는 거치지 않습니다).
  const handlePaymentComplete = async () => {
    try {
      const booking = await createBooking(user.id, { screeningId, seatIds: selectedSeats, ticketCounts })
      window.alert(
        `결제가 완료되었습니다!\n"${booking.movieTitle}" ${booking.theaterName} ${booking.showDate} ${booking.showtime}\n` +
          `좌석: ${booking.seats.join(', ')} · ${booking.totalPrice.toLocaleString()}원`,
      )
      setSelectedSeats([])
      setTicketCounts({ adult: 0, teen: 0, child: 0, senior: 0 })
      setShowPaymentWidget(false)
      refreshSeats()
    } catch (error) {
      window.alert(error.message)
      refreshSeats()
    }
  }

  return (
    <div className="min-h-svh bg-white text-gray-900">
      <div className="mx-auto max-w-4xl px-6 py-8">
        <Link to="/booking" className="text-sm text-indigo-600 hover:underline">
          ← 상영중인 영화 목록으로
        </Link>

        {status === 'loading' && <p className="mt-6 text-gray-500">불러오는 중...</p>}
        {status === 'error' && <p className="mt-6 text-red-500">영화 정보를 불러오지 못했습니다.</p>}

        {status === 'ready' && (
          <>
            <div className="mt-4 flex items-center gap-4 border-b border-gray-200 pb-6">
              <div className="h-24 w-16 shrink-0 overflow-hidden rounded-lg">
                {movie.posterUrl ? (
                  <img src={movie.posterUrl} alt={movie.title} className="h-full w-full object-cover" />
                ) : (
                  <PosterPlaceholder compact className="bg-gradient-to-br from-indigo-600 to-purple-700" />
                )}
              </div>
              <div>
                <h1 className="text-xl font-bold">{movie.title}</h1>
                <p className="mt-1 text-sm text-gray-500">
                  {['새싹시네마', theater, movie.runtimeMinutes ? `${movie.runtimeMinutes}분` : null, movie.ageRating]
                    .filter(Boolean)
                    .join(' · ')}
                </p>
              </div>
            </div>

            {!hasScreenings && (
              <p className="mt-6 text-sm text-gray-500">이번 주에는 이 영화의 상영 일정이 없습니다.</p>
            )}

            {hasScreenings && (
              <>
                <div className="grid grid-cols-1 gap-6 border-b border-gray-200 py-6 sm:grid-cols-3">
                  <div>
                    <h2 className="mb-2 text-sm font-semibold text-gray-500">상영관</h2>
                    <div className="flex flex-wrap gap-2">
                      {theaterOptions.map((name) => (
                        <PillButton key={name} active={theater === name} onClick={() => setTheater(name)}>
                          {name}
                        </PillButton>
                      ))}
                    </div>
                  </div>

                  <div>
                    <h2 className="mb-2 text-sm font-semibold text-gray-500">날짜</h2>
                    <div className="flex flex-wrap gap-2">
                      {dateOptions.map((date) => (
                        <PillButton
                          key={date.value}
                          active={showDate === date.value}
                          onClick={() => setShowDate(date.value)}
                        >
                          {date.label}
                        </PillButton>
                      ))}
                    </div>
                  </div>

                  <div>
                    <h2 className="mb-2 text-sm font-semibold text-gray-500">회차</h2>
                    <div className="flex flex-wrap gap-2">
                      {showtimeOptions.length === 0 && (
                        <p className="text-sm text-gray-400">선택한 날짜에 상영 회차가 없습니다.</p>
                      )}
                      {showtimeOptions.map((time) => (
                        <PillButton key={time} active={showtime === time} onClick={() => setShowtime(time)}>
                          {time}
                        </PillButton>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="border-b border-gray-200 py-6">
                  <div className="mb-3 flex items-center gap-2">
                    <h2 className="text-sm font-semibold text-gray-500">인원 선택</h2>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                        holidayPricing ? 'bg-rose-100 text-rose-600' : 'bg-indigo-100 text-indigo-600'
                      }`}
                    >
                      {TIME_PERIOD_LABELS[timePeriod]} · {holidayPricing ? '주말·공휴일 요금' : '주중 요금'}
                    </span>
                  </div>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    {AGE_CATEGORIES.map((category) => {
                      const disallowed = disallowedCategories.includes(category.key)
                      return (
                        <div
                          key={category.key}
                          className={`flex items-center justify-between rounded-lg border px-4 py-3 ${
                            disallowed ? 'border-gray-100 bg-gray-50 opacity-60' : 'border-gray-200'
                          }`}
                        >
                          <div>
                            <p className="text-sm font-semibold text-gray-900">{category.label}</p>
                            <p className="text-xs text-gray-500">{category.sublabel}</p>
                            {disallowed ? (
                              <p className="mt-0.5 text-xs font-medium text-rose-500">
                                {movie.ageRating} 영화라 선택할 수 없어요
                              </p>
                            ) : (
                              <p className="mt-0.5 text-xs text-gray-500">
                                {priceFor(category.key, showDate, showtime).toLocaleString()}원
                              </p>
                            )}
                          </div>
                          <div className="flex items-center gap-3">
                            <button
                              type="button"
                              disabled={disallowed}
                              onClick={() => updateTicketCount(category.key, -1)}
                              className="flex h-7 w-7 items-center justify-center rounded-full bg-gray-100 text-gray-700 hover:bg-gray-200 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              -
                            </button>
                            <span className="w-4 text-center text-sm font-semibold">{ticketCounts[category.key]}</span>
                            <button
                              type="button"
                              disabled={disallowed}
                              onClick={() => updateTicketCount(category.key, 1)}
                              className="flex h-7 w-7 items-center justify-center rounded-full bg-gray-100 text-gray-700 hover:bg-gray-200 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              +
                            </button>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                  {totalTickets > 0 && (
                    <p className="mt-3 text-xs text-gray-500">
                      총 {totalTickets}명 · 좌석을 {totalTickets}석 선택해주세요.
                    </p>
                  )}
                </div>

                <div className="py-8">
                  <SeatMap seats={seats} selectedSeats={selectedSeats} onToggleSeat={toggleSeat} />
                </div>

                <div className="flex items-center justify-between border-t border-gray-200 pt-6">
                  <div>
                    <p className="text-sm text-gray-600">
                      선택 좌석:{' '}
                      {selectedSeatLabels.length > 0 ? (
                        <span className="font-semibold text-indigo-600">
                          {selectedSeatLabels.join(', ')} ({selectedSeatLabels.length}석)
                        </span>
                      ) : (
                        '없음'
                      )}
                    </p>
                    {totalTickets === 0 && <p className="mt-1 text-xs text-rose-500">인원을 먼저 선택해주세요.</p>}
                    {totalTickets > 0 && !seatsMatchTickets && (
                      <p className="mt-1 text-xs text-rose-500">
                        선택한 인원({totalTickets}명)만큼 좌석을 선택해주세요.
                      </p>
                    )}
                  </div>
                  <p className="text-xl font-bold">{totalPrice.toLocaleString()}원</p>
                  {!showPaymentWidget && (
                    <button
                      type="button"
                      disabled={!seatsMatchTickets}
                      onClick={() => setShowPaymentWidget(true)}
                      className="rounded-lg bg-indigo-600 px-6 py-3 text-sm font-semibold text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:bg-gray-300"
                    >
                      결제하기
                    </button>
                  )}
                </div>

                {showPaymentWidget && (
                  <div className="mt-6 border-t border-gray-200 pt-6">
                    <TossPaymentWidget amount={totalPrice} onComplete={handlePaymentComplete} />
                  </div>
                )}
              </>
            )}
          </>
        )}
      </div>
    </div>
  )
}

export default BookingSeatPage
