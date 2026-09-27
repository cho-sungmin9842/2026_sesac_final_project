import { useEffect, useState } from 'react'
import { getReservedSeats } from '../../api/bookingApi'
import { getNowShowing } from '../../api/movieApi'
import { getBookingDates, SEATS_PER_ROW, SEAT_ROWS, SHOWTIMES, THEATERS } from '../Booking/bookingData'
import StatCard from './components/StatCard'

const TOTAL_SEATS = SEAT_ROWS.length * SEATS_PER_ROW
const DATES = getBookingDates()

// 회차(SHOWTIMES)별로 실제 예약된 좌석을 백엔드(bookings 테이블)에서 조회해 예매 현황을 보여줍니다.
// 영화 목록은 사용자 예매 탭(BookingListPage)과 완전히 같은 "현재 상영중인 영화"를 그대로 씁니다.
function AdminScreeningsPage() {
  const [movies, setMovies] = useState([])
  const [moviesStatus, setMoviesStatus] = useState('loading') // loading | ready | error
  const [selectedDate, setSelectedDate] = useState(DATES[0].value)
  const [selectedMovieId, setSelectedMovieId] = useState('')
  const [selectedTheater, setSelectedTheater] = useState(THEATERS[0])
  const [occupancy, setOccupancy] = useState([])
  const [occupancyStatus, setOccupancyStatus] = useState('idle') // idle | loading | ready | error

  useEffect(() => {
    let cancelled = false
    getNowShowing()
      .then((result) => {
        if (cancelled) return
        setMovies(result.movies)
        if (result.movies.length > 0) {
          setSelectedMovieId(result.movies[0].id)
        }
        setMoviesStatus('ready')
      })
      .catch(() => {
        if (cancelled) return
        setMoviesStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!selectedMovieId) return undefined
    let cancelled = false
    setOccupancyStatus('loading')

    Promise.all(
      SHOWTIMES.map((showtime) =>
        getReservedSeats({ movieId: selectedMovieId, theater: selectedTheater, showDate: selectedDate, showtime }).then(
          (dto) => ({ showtime, seats: dto.seats }),
        ),
      ),
    )
      .then((rows) => {
        if (cancelled) return
        setOccupancy(rows)
        setOccupancyStatus('ready')
      })
      .catch(() => {
        if (cancelled) return
        setOccupancyStatus('error')
      })

    return () => {
      cancelled = true
    }
  }, [selectedMovieId, selectedTheater, selectedDate])

  const totalReserved = occupancy.reduce((sum, row) => sum + row.seats.length, 0)
  const totalCapacity = TOTAL_SEATS * SHOWTIMES.length
  const occupancyRate = totalCapacity > 0 ? Math.round((totalReserved / totalCapacity) * 100) : 0

  return (
    <div className="px-8 py-6">
      <h1 className="text-xl font-bold text-white">예매/상영관 현황</h1>
      <p className="mt-1 text-sm text-gray-400">
        일자별로 상영 영화와 상영관을 선택하면 회차별 예매 현황을 볼 수 있습니다.
      </p>

      <div className="mt-6 flex flex-wrap gap-3">
        <select
          value={selectedDate}
          onChange={(event) => setSelectedDate(event.target.value)}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm text-gray-200 focus:outline-none"
        >
          {DATES.map((date) => (
            <option key={date.value} value={date.value}>
              {date.label}
            </option>
          ))}
        </select>

        <select
          value={selectedMovieId}
          onChange={(event) => setSelectedMovieId(event.target.value)}
          disabled={moviesStatus !== 'ready'}
          className="min-w-[240px] rounded-lg bg-slate-900 px-3 py-2 text-sm text-gray-200 focus:outline-none"
        >
          {moviesStatus === 'loading' && <option>영화 목록 불러오는 중...</option>}
          {moviesStatus === 'error' && <option>영화 목록을 불러오지 못했습니다</option>}
          {movies.map((movie) => (
            <option key={movie.id} value={movie.id}>
              {movie.title}
            </option>
          ))}
        </select>

        <select
          value={selectedTheater}
          onChange={(event) => setSelectedTheater(event.target.value)}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm text-gray-200 focus:outline-none"
        >
          {THEATERS.map((theater) => (
            <option key={theater} value={theater}>
              {theater}
            </option>
          ))}
        </select>
      </div>

      <div className="mt-6 flex gap-4">
        <StatCard label="전체 좌석(전 회차 합계)" value={`${totalCapacity}석`} />
        <StatCard label="예매된 좌석" value={`${totalReserved}석`} />
        <StatCard label="예매율" value={`${occupancyRate}%`} />
      </div>

      <div className="mt-6 rounded-xl bg-slate-900/60 p-4">
        {occupancyStatus === 'loading' && <p className="text-sm text-gray-400">예매 현황을 불러오는 중...</p>}
        {occupancyStatus === 'error' && (
          <p className="text-sm text-red-400">예매 현황을 불러오지 못했습니다. 다시 시도해주세요.</p>
        )}
        {occupancyStatus === 'ready' && (
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-white/10 text-gray-400">
                <th className="pb-3 font-medium">회차</th>
                <th className="pb-3 font-medium">예매 좌석 수</th>
                <th className="pb-3 font-medium">예매율</th>
                <th className="pb-3 font-medium">예매된 좌석</th>
              </tr>
            </thead>
            <tbody>
              {occupancy.map((row) => (
                <tr key={row.showtime} className="border-b border-white/5 last:border-0">
                  <td className="py-3 font-semibold text-white">{row.showtime}</td>
                  <td className="py-3 text-gray-300">
                    {row.seats.length} / {TOTAL_SEATS}
                  </td>
                  <td className="py-3 text-gray-300">{Math.round((row.seats.length / TOTAL_SEATS) * 100)}%</td>
                  <td className="py-3 text-gray-400">{row.seats.length > 0 ? row.seats.join(', ') : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default AdminScreeningsPage
