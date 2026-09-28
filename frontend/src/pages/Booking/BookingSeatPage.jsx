import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { getMovieDetail } from '../../api/movieApi'
import { createBooking, getReservedSeats } from '../../api/bookingApi'
import PosterPlaceholder from '../../components/common/PosterPlaceholder'
import SeatMap from './components/SeatMap'
import {
  ACCESSIBLE_SEATS,
  AGE_CATEGORIES,
  SHOWTIMES,
  THEATERS,
  getBookingDates,
  isWeekendOrHoliday,
  priceFor,
} from './bookingData'

const DATES = getBookingDates()

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
  const [status, setStatus] = useState('loading')
  const [theater, setTheater] = useState(THEATERS[0])
  const [dateIndex, setDateIndex] = useState(0)
  const [showtime, setShowtime] = useState(SHOWTIMES[0])
  const [selectedSeats, setSelectedSeats] = useState([])
  const [reservedSeats, setReservedSeats] = useState(new Set())
  const [ticketCounts, setTicketCounts] = useState({ adult: 0, teen: 0, child: 0, senior: 0 })

  useEffect(() => {
    let cancelled = false
    setStatus('loading')
    getMovieDetail(id)
      .then((dto) => {
        if (cancelled) return
        setMovie(dto)
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

  const showDate = DATES[dateIndex].value

  const refreshReservedSeats = () => {
    getReservedSeats({ movieId: id, theater, showDate, showtime }).then((dto) => {
      setReservedSeats(new Set(dto.seats))
    })
  }

  useEffect(() => {
    refreshReservedSeats()
    // 상영관/날짜/회차가 바뀌면 좌석 배치도 달라지니 선택은 초기화합니다.
    setSelectedSeats([])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, theater, showDate, showtime])

  const toggleSeat = (seatId) => {
    const isCurrentlySelected = selectedSeats.includes(seatId)
    if (!isCurrentlySelected && ACCESSIBLE_SEATS.has(seatId)) {
      const confirmed = window.confirm('장애인석을 선택했습니다.\n장애인석을 선택하시겠습니까?')
      if (!confirmed) return
    }
    setSelectedSeats((prev) =>
      prev.includes(seatId) ? prev.filter((seat) => seat !== seatId) : [...prev, seatId],
    )
  }

  const updateTicketCount = (key, delta) => {
    setTicketCounts((prev) => ({ ...prev, [key]: Math.max(0, prev[key] + delta) }))
  }

  const holidayPricing = isWeekendOrHoliday(showDate)
  const totalTickets = Object.values(ticketCounts).reduce((sum, count) => sum + count, 0)
  const totalPrice = AGE_CATEGORIES.reduce(
    (sum, category) => sum + ticketCounts[category.key] * priceFor(category.key, showDate),
    0,
  )
  // 좌석은 인원수만큼 정확히 골라야 결제할 수 있습니다(누가 어느 좌석인지까지는 구분하지 않습니다).
  const seatsMatchTickets = totalTickets > 0 && selectedSeats.length === totalTickets

  const handlePayment = async () => {
    if (!seatsMatchTickets) return
    try {
      await createBooking(user.id, {
        movieId: id,
        movieTitle: movie.title,
        theater,
        showDate,
        showtime,
        seats: selectedSeats,
        ticketCounts,
      })
      window.alert(`${selectedSeats.length}석 (${totalPrice.toLocaleString()}원) 결제가 완료되었습니다.`)
      setSelectedSeats([])
      setTicketCounts({ adult: 0, teen: 0, child: 0, senior: 0 })
      refreshReservedSeats()
    } catch (error) {
      window.alert(error.message)
      refreshReservedSeats()
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
                  새싹시네마 · 2관 · {movie.runtimeMinutes}분 · {movie.ageRating}
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-6 border-b border-gray-200 py-6 sm:grid-cols-3">
              <div>
                <h2 className="mb-2 text-sm font-semibold text-gray-500">상영관</h2>
                <div className="flex flex-wrap gap-2">
                  {THEATERS.map((name) => (
                    <PillButton key={name} active={theater === name} onClick={() => setTheater(name)}>
                      {name}
                    </PillButton>
                  ))}
                </div>
              </div>

              <div>
                <h2 className="mb-2 text-sm font-semibold text-gray-500">날짜</h2>
                <div className="flex flex-wrap gap-2">
                  {DATES.map((date, index) => (
                    <PillButton key={date.value} active={dateIndex === index} onClick={() => setDateIndex(index)}>
                      {date.label}
                    </PillButton>
                  ))}
                </div>
              </div>

              <div>
                <h2 className="mb-2 text-sm font-semibold text-gray-500">회차</h2>
                <div className="flex flex-wrap gap-2">
                  {SHOWTIMES.map((time) => (
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
                  {holidayPricing ? '주말·공휴일 요금' : '주중 요금'}
                </span>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {AGE_CATEGORIES.map((category) => (
                  <div
                    key={category.key}
                    className="flex items-center justify-between rounded-lg border border-gray-200 px-4 py-3"
                  >
                    <div>
                      <p className="text-sm font-semibold text-gray-900">{category.label}</p>
                      <p className="text-xs text-gray-500">{category.sublabel}</p>
                      <p className="mt-0.5 text-xs text-gray-500">
                        {priceFor(category.key, showDate).toLocaleString()}원
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <button
                        type="button"
                        onClick={() => updateTicketCount(category.key, -1)}
                        className="flex h-7 w-7 items-center justify-center rounded-full bg-gray-100 text-gray-700 hover:bg-gray-200"
                      >
                        -
                      </button>
                      <span className="w-4 text-center text-sm font-semibold">{ticketCounts[category.key]}</span>
                      <button
                        type="button"
                        onClick={() => updateTicketCount(category.key, 1)}
                        className="flex h-7 w-7 items-center justify-center rounded-full bg-gray-100 text-gray-700 hover:bg-gray-200"
                      >
                        +
                      </button>
                    </div>
                  </div>
                ))}
              </div>
              {totalTickets > 0 && (
                <p className="mt-3 text-xs text-gray-500">
                  총 {totalTickets}명 · 좌석을 {totalTickets}석 선택해주세요.
                </p>
              )}
            </div>

            <div className="py-8">
              <SeatMap reservedSeats={reservedSeats} selectedSeats={selectedSeats} onToggleSeat={toggleSeat} />
            </div>

            <div className="flex items-center justify-between border-t border-gray-200 pt-6">
              <div>
                <p className="text-sm text-gray-600">
                  선택 좌석:{' '}
                  {selectedSeats.length > 0 ? (
                    <span className="font-semibold text-indigo-600">
                      {selectedSeats.join(', ')} ({selectedSeats.length}석)
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
              <button
                type="button"
                disabled={!seatsMatchTickets}
                onClick={handlePayment}
                className="rounded-lg bg-indigo-600 px-6 py-3 text-sm font-semibold text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:bg-gray-300"
              >
                결제하기
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export default BookingSeatPage
