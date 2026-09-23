import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { getMovieDetail } from '../../api/movieApi'
import { createBooking, getReservedSeats } from '../../api/bookingApi'
import PosterPlaceholder from '../../components/common/PosterPlaceholder'
import SeatMap from './components/SeatMap'
import { PRICE_PER_SEAT, SHOWTIMES, THEATERS, getBookingDates } from './bookingData'

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
    setSelectedSeats((prev) =>
      prev.includes(seatId) ? prev.filter((seat) => seat !== seatId) : [...prev, seatId],
    )
  }

  const totalPrice = selectedSeats.length * PRICE_PER_SEAT

  const handlePayment = async () => {
    if (selectedSeats.length === 0) return
    try {
      await createBooking(user.id, {
        movieId: id,
        movieTitle: movie.title,
        theater,
        showDate,
        showtime,
        seats: selectedSeats,
      })
      window.alert(`${selectedSeats.length}석 (${totalPrice.toLocaleString()}원) 결제가 완료되었습니다.`)
      setSelectedSeats([])
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
                  무비픽시네마 {theater} · 2관 · {movie.runtimeMinutes}분 · {movie.ageRating}
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

            <div className="py-8">
              <SeatMap reservedSeats={reservedSeats} selectedSeats={selectedSeats} onToggleSeat={toggleSeat} />
            </div>

            <div className="flex items-center justify-between border-t border-gray-200 pt-6">
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
              <p className="text-xl font-bold">{totalPrice.toLocaleString()}원</p>
              <button
                type="button"
                disabled={selectedSeats.length === 0}
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
