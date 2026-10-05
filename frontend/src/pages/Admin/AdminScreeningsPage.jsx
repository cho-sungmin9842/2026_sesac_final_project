import { useEffect, useState } from 'react'
import { getScreenings, getScreeningSeats } from '../../api/screeningApi'
import { getNowShowing } from '../../api/movieApi'
import StatCard from './components/StatCard'
import AdminSeatMap from './components/AdminSeatMap'

function toHourMinute(timeStr) {
  return timeStr.slice(0, 5)
}

// 상영관/날짜/회차별 실제 좌석 상태(screenings/seats 테이블)를 조회해 예매 현황을 보여줍니다.
// 영화 목록은 사용자 예매 탭(BookingListPage)과 완전히 같은 "현재 상영중인 영화"를 그대로 씁니다.
function AdminScreeningsPage() {
  const [movies, setMovies] = useState([])
  const [moviesStatus, setMoviesStatus] = useState('loading') // loading | ready | error
  const [selectedMovieId, setSelectedMovieId] = useState('')
  const [screenings, setScreenings] = useState([])
  const [selectedTheater, setSelectedTheater] = useState('')
  const [selectedDate, setSelectedDate] = useState('')
  const [occupancy, setOccupancy] = useState([])
  const [occupancyStatus, setOccupancyStatus] = useState('idle') // idle | loading | ready | error
  const [selectedScreeningId, setSelectedScreeningId] = useState(null)

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
    getScreenings(selectedMovieId).then((list) => {
      if (cancelled) return
      setScreenings(list)
      const firstTheater = [...new Set(list.map((s) => s.theaterName))].sort()[0] ?? ''
      setSelectedTheater(firstTheater)
    })
    return () => {
      cancelled = true
    }
  }, [selectedMovieId])

  const theaterOptions = [...new Set(screenings.map((s) => s.theaterName))].sort()
  const dateOptions = [...new Set(screenings.filter((s) => s.theaterName === selectedTheater).map((s) => s.date))].sort()

  useEffect(() => {
    if (dateOptions.length === 0) {
      if (selectedDate !== '') setSelectedDate('')
      return
    }
    if (!dateOptions.includes(selectedDate)) {
      setSelectedDate(dateOptions[0])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedTheater, screenings])

  const matchingScreenings = screenings
    .filter((s) => s.theaterName === selectedTheater && s.date === selectedDate)
    .sort((a, b) => a.startTime.localeCompare(b.startTime))

  useEffect(() => {
    if (matchingScreenings.length === 0) {
      setOccupancy([])
      return undefined
    }
    let cancelled = false
    setOccupancyStatus('loading')

    Promise.all(
      matchingScreenings.map((screening) =>
        getScreeningSeats(screening.id).then((seats) => ({
          screeningId: screening.id,
          showtime: toHourMinute(screening.startTime),
          totalSeats: seats.length,
          bookedSeats: seats.filter((seat) => seat.status === 'BOOKED'),
          seats,
        })),
      ),
    )
      .then((rows) => {
        if (cancelled) return
        setOccupancy(rows)
        setSelectedScreeningId(rows.length > 0 ? rows[0].screeningId : null)
        setOccupancyStatus('ready')
      })
      .catch(() => {
        if (cancelled) return
        setOccupancyStatus('error')
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedTheater, selectedDate, screenings])

  const totalReserved = occupancy.reduce((sum, row) => sum + row.bookedSeats.length, 0)
  const totalCapacity = occupancy.reduce((sum, row) => sum + row.totalSeats, 0)
  const occupancyRate = totalCapacity > 0 ? Math.round((totalReserved / totalCapacity) * 100) : 0

  return (
    <div className="px-8 py-6">
      <h1 className="text-xl font-bold text-white">예매/상영관 현황</h1>
      <p className="mt-1 text-sm text-gray-400">
        일자별로 상영 영화와 상영관을 선택하면 회차별 예매 현황을 볼 수 있습니다.
      </p>

      <div className="mt-6 flex flex-wrap gap-3">
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
          disabled={theaterOptions.length === 0}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm text-gray-200 focus:outline-none"
        >
          {theaterOptions.length === 0 && <option>상영 일정 없음</option>}
          {theaterOptions.map((theater) => (
            <option key={theater} value={theater}>
              {theater}
            </option>
          ))}
        </select>

        <select
          value={selectedDate}
          onChange={(event) => setSelectedDate(event.target.value)}
          disabled={dateOptions.length === 0}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm text-gray-200 focus:outline-none"
        >
          {dateOptions.length === 0 && <option>상영 일정 없음</option>}
          {dateOptions.map((date) => (
            <option key={date} value={date}>
              {date}
            </option>
          ))}
        </select>
      </div>

      <div className="mt-6 flex gap-4">
        <StatCard label="전체 좌석(선택한 날짜 전 회차 합계)" value={`${totalCapacity}석`} />
        <StatCard label="예매된 좌석" value={`${totalReserved}석`} />
        <StatCard label="예매율" value={`${occupancyRate}%`} />
      </div>

      <div className="mt-6 rounded-xl bg-slate-900/60 p-4">
        {matchingScreenings.length === 0 && (
          <p className="text-sm text-gray-400">이 영화는 선택한 상영관/날짜에 상영 일정이 없습니다.</p>
        )}
        {matchingScreenings.length > 0 && occupancyStatus === 'loading' && (
          <p className="text-sm text-gray-400">예매 현황을 불러오는 중...</p>
        )}
        {occupancyStatus === 'error' && (
          <p className="text-sm text-red-400">예매 현황을 불러오지 못했습니다. 다시 시도해주세요.</p>
        )}
        {occupancyStatus === 'ready' && occupancy.length > 0 && (
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
                <tr
                  key={row.showtime}
                  onClick={() => setSelectedScreeningId(row.screeningId)}
                  className={`cursor-pointer border-b border-white/5 last:border-0 hover:bg-white/5 ${
                    selectedScreeningId === row.screeningId ? 'bg-indigo-500/10' : ''
                  }`}
                >
                  <td className="py-3 font-semibold text-white">{row.showtime}</td>
                  <td className="py-3 text-gray-300">
                    {row.bookedSeats.length} / {row.totalSeats}
                  </td>
                  <td className="py-3 text-gray-300">
                    {row.totalSeats > 0 ? Math.round((row.bookedSeats.length / row.totalSeats) * 100) : 0}%
                  </td>
                  <td className="py-3 text-gray-400">
                    {row.bookedSeats.length > 0
                      ? row.bookedSeats.map((seat) => `${seat.rowLabel}${seat.colNo}`).join(', ')
                      : '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {occupancyStatus === 'ready' && occupancy.length > 0 && (
        <div className="mt-6 rounded-xl bg-slate-900/60 p-6">
          {occupancy
            .filter((row) => row.screeningId === selectedScreeningId)
            .map((row) => (
              <div key={row.screeningId}>
                <p className="mb-4 text-sm text-gray-400">
                  <span className="font-semibold text-white">{row.showtime}</span> 회차 좌석 현황
                </p>
                <AdminSeatMap seats={row.seats} />
              </div>
            ))}
        </div>
      )}
    </div>
  )
}

export default AdminScreeningsPage
