import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { getMovieDetail } from '../../api/movieApi'
import { getScreenings, getScreeningSeats, holdSeat, releaseSeatHold } from '../../api/screeningApi'
import { createBooking } from '../../api/bookingApi'
import PosterPlaceholder from '../../components/common/PosterPlaceholder'
import ConfirmDialog from '../../components/common/ConfirmDialog'
import InfoDialog from '../../components/common/InfoDialog'
import SeatMap from './components/SeatMap'
import TossPaymentWidget from './components/TossPaymentWidget'
import {
  AGE_CATEGORIES,
  TIME_PERIOD_LABELS,
  getDisallowedCategories,
  isWeekendOrHoliday,
  priceFor,
  requiresTeenAgeWarning,
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

// 좌석 선택을 "선점(hold)"으로 서버에 반영한 뒤, 다른 사용자의 선택/예매도 바로 반영되도록 주기적으로
// 좌석맵을 다시 불러오고(그 사이 내가 선택한 좌석의 TTL도 같이 갱신) - NotificationBell.jsx의 폴링
// 패턴을 그대로 따릅니다.
const SEAT_POLL_INTERVAL_MS = 5000

function PillButton({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-lg px-4 py-2 text-sm font-semibold ${
        active ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-gray-300 hover:bg-slate-700'
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
  // 결제 요청이 진행 중인 동안은 좌석맵 폴링이 선택 좌석을 "정리"(reconcile)하지 않도록 막는 플래그 -
  // 응답을 기다리는 사이에 늦게 도착한 폴링 결과 때문에 제출 중인 좌석이 선택 해제되는 걸 방지합니다.
  const [isSubmitting, setIsSubmitting] = useState(false)
  // setInterval 콜백은 생성 시점에 state를 클로저로 캡처해버리므로(effect 의존성을 screeningId/user.id
  // 로만 고정해 매 선택마다 interval을 새로 걸지 않기 위해), 항상 최신 값을 읽어야 하는 두 상태는 ref로도
  // 같이 들고 있습니다.
  const selectedSeatsRef = useRef([])
  const isSubmittingRef = useRef(false)
  const screeningIdRef = useRef(null)
  // 네이티브 confirm/alert 대신 쓰는 커스텀 다이얼로그 상태들.
  const [pendingWheelchairSeat, setPendingWheelchairSeat] = useState(null)
  // "15세이상관람가" 영화에서 청소년 인원을 +1 할 때마다(인원 구분이 "청소년"만으로는 만 12~14세와
  // 15~18세를 구분 못 하므로) 띄우는 확인 팝업 상태입니다.
  const [showTeenAgeConfirm, setShowTeenAgeConfirm] = useState(false)
  const [showPaymentConfirm, setShowPaymentConfirm] = useState(false)
  const [resultDialog, setResultDialog] = useState(null) // { title, message, tone }

  // 페이지 이동 시 맨 위로 스크롤하는 건 App.jsx의 전역 ScrollToTop이 처리합니다(라우트가
  // /booking/:id라 영화를 바꿔도 경로 자체가 바뀌어 거기서 함께 처리됩니다).

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
    getScreeningSeats(screeningId, user.id).then(setSeats)
  }

  useEffect(() => {
    selectedSeatsRef.current = selectedSeats
  }, [selectedSeats])

  useEffect(() => {
    isSubmittingRef.current = isSubmitting
  }, [isSubmitting])

  useEffect(() => {
    screeningIdRef.current = screeningId
  }, [screeningId])

  // 앱 내부 링크로 다른 화면으로 이동하면 아래 screeningId effect의 cleanup이 선점을 풀어주지만,
  // 주소창 이동/새로고침/탭 닫기처럼 브라우저가 페이지 자체를 통째로 치워버리는 경우는 React가 그
  // cleanup을 실행할 틈도 없이 끝나버립니다(선점이 TTL 5분 동안 풀리지 않고 남는 버그 - 2026-10-18
  // 실측). pagehide는 이런 상황에서도 실행이 보장되는 몇 안 되는 이벤트라, 여기서 한 번 더 해제를
  // 시도합니다. keepalive를 줘서 문서가 이미 닫히는 중이어도 이 요청만은 전송을 시도합니다(100%
  // 보장되는 방법은 아니지만, 아무 조치가 없는 것보다는 훨씬 낫습니다 - 그래도 안 풀리면 기존처럼
  // TTL 만료로 결국 자동 해제됩니다).
  useEffect(() => {
    const handlePageHide = () => {
      const currentScreeningId = screeningIdRef.current
      if (!currentScreeningId) return
      selectedSeatsRef.current.forEach((seatId) => {
        releaseSeatHold(currentScreeningId, seatId, user.id, true).catch(() => {})
      })
    }
    window.addEventListener('pagehide', handlePageHide)
    return () => window.removeEventListener('pagehide', handlePageHide)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.id])

  useEffect(() => {
    if (!screeningId) {
      setSeats([])
      return undefined
    }
    refreshSeats()
    // 상영관/날짜/회차가 바뀌면 좌석 배치도 달라지니 선택과 결제위젯은 초기화합니다.
    setSelectedSeats([])
    setShowPaymentWidget(false)
    // 다른 상영으로 넘어가거나 화면을 벗어날 때, 그때까지 선택(선점)해뒀던 좌석들을 best-effort로
    // 해제합니다 - 실패해도 TTL이 지나면 자동으로 풀리므로 에러는 그냥 무시합니다.
    return () => {
      selectedSeatsRef.current.forEach((seatId) => {
        releaseSeatHold(screeningId, seatId, user.id).catch(() => {})
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screeningId])

  // 좌석맵을 주기적으로 다시 불러와 다른 사용자의 선택/예매를 반영하고, 그 사이 내가 선택해둔 좌석들의
  // 선점 TTL을 계속 갱신합니다(결제위젯이 떠 있는 동안도 포함). 의존성을 screeningId/user.id로만 고정해
  // 좌석을 빠르게 여러 번 클릭해도 interval이 매번 재설정되며 아예 안 울리는 일이 없게 합니다.
  useEffect(() => {
    if (!screeningId) return undefined
    const tick = () => {
      if (isSubmittingRef.current) return
      getScreeningSeats(screeningId, user.id)
        .then((latestSeats) => {
          setSeats(latestSeats)
          const latestById = new Map(latestSeats.map((seat) => [seat.id, seat]))
          const stillValid = selectedSeatsRef.current.filter((seatId) => {
            const latest = latestById.get(seatId)
            return latest && (latest.status === 'AVAILABLE' || latest.heldByMe)
          })
          if (stillValid.length !== selectedSeatsRef.current.length) {
            setSelectedSeats(stillValid)
          }
          stillValid.forEach((seatId) => {
            holdSeat(screeningId, seatId, user.id).catch(() => {})
          })
        })
        .catch(() => {})
    }
    const interval = setInterval(tick, SEAT_POLL_INTERVAL_MS)
    return () => clearInterval(interval)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screeningId, user.id])

  // 좌석 선택은 곧 서버에 임시 선점(hold)을 거는 것이라 비동기입니다 - 선점에 성공해야 실제로
  // selectedSeats에 추가되고, 이미 다른 사용자가 선점/예매한 좌석이면 에러로 안내하고 최신 좌석맵을
  // 다시 불러옵니다. 해제는 결과를 기다리지 않고 낙관적으로 처리합니다(fire-and-forget).
  const applySeatSelection = async (seat) => {
    const isCurrentlySelected = selectedSeats.includes(seat.id)
    if (isCurrentlySelected) {
      setSelectedSeats((prev) => prev.filter((seatId) => seatId !== seat.id))
      releaseSeatHold(screeningId, seat.id, user.id).catch(() => {})
      return
    }
    try {
      await holdSeat(screeningId, seat.id, user.id)
      setSelectedSeats((prev) => [...prev, seat.id])
    } catch (error) {
      setResultDialog({ title: '선택할 수 없는 좌석이에요', message: error.message, tone: 'error' })
      refreshSeats()
    }
  }

  // 장애인석(휠체어석)을 새로 선택하려는 경우에만, 실수로 누른 게 아닌지 커스텀 다이얼로그로 한 번 더
  // 확인합니다(이미 선택된 장애인석을 다시 눌러 해제할 때는 확인 없이 바로 처리).
  const toggleSeat = (seat) => {
    // 결제 확인 다이얼로그에서 이미 "결제 후에는 좌석을 바꿀 수 없다"고 안내했으므로, 결제위젯이 뜬
    // 뒤에는 좌석 선택 자체를 막습니다 - 안 그러면 인원수와 좌석 수가 달라진 채로 결제를 시도하다가
    // 서버 쪽에서야 거부당하는 혼란스러운 흐름이 됩니다(2026-10-25 실측).
    if (showPaymentWidget) return
    const isCurrentlySelected = selectedSeats.includes(seat.id)
    if (!isCurrentlySelected && seat.seatType === 'WHEELCHAIR') {
      setPendingWheelchairSeat(seat)
      return
    }
    applySeatSelection(seat)
  }

  const confirmWheelchairSeat = () => {
    if (pendingWheelchairSeat) applySeatSelection(pendingWheelchairSeat)
    setPendingWheelchairSeat(null)
  }

  const handleProceedToPayment = () => {
    setShowPaymentConfirm(true)
  }

  const updateTicketCount = (key, delta) => {
    // 좌석 선택과 같은 이유로, 결제위젯이 뜬 뒤에는 인원수도 바꿀 수 없게 막습니다.
    if (showPaymentWidget) return
    if (disallowedCategories.includes(key)) return
    // "15세이상관람가" 영화는 청소년(만 12~18세) 중 만 12~14세는 관람할 수 없어서, 청소년 인원을 늘릴
    // 때마다 먼저 확인 팝업을 띄웁니다(줄일 때는 경고가 필요 없어 delta > 0일 때만).
    if (key === 'teen' && delta > 0 && requiresTeenAgeWarning(movie.ageRating)) {
      setShowTeenAgeConfirm(true)
      return
    }
    setTicketCounts((prev) => ({ ...prev, [key]: Math.max(0, prev[key] + delta) }))
  }

  const confirmTeenAgeIncrease = () => {
    setTicketCounts((prev) => ({ ...prev, teen: prev.teen + 1 }))
    setShowTeenAgeConfirm(false)
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
    setIsSubmitting(true)
    try {
      const booking = await createBooking(user.id, { screeningId, seatIds: selectedSeats, ticketCounts })
      setResultDialog({
        title: '결제가 완료됐어요 🎬',
        message:
          `"${booking.movieTitle}" ${booking.theaterName} ${booking.showDate} ${booking.showtime}\n` +
          `좌석: ${booking.seats.join(', ')} · ${booking.totalPrice.toLocaleString()}원`,
      })
      setSelectedSeats([])
      setTicketCounts({ adult: 0, teen: 0, child: 0, senior: 0 })
      setShowPaymentWidget(false)
      refreshSeats()
    } catch (error) {
      setResultDialog({ title: '결제에 실패했어요', message: error.message, tone: 'error' })
      refreshSeats()
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-svh bg-slate-950 text-gray-100">
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
        <Link to="/booking" className="text-sm text-indigo-400 hover:text-indigo-300 hover:underline">
          ← 상영중인 영화 목록으로
        </Link>

        {status === 'loading' && <p className="mt-6 text-gray-400">불러오는 중...</p>}
        {status === 'error' && <p className="mt-6 text-red-400">영화 정보를 불러오지 못했습니다.</p>}

        {status === 'ready' && (
          <>
            <div className="mt-4 flex items-center gap-4 border-b border-white/10 pb-6">
              <div className="h-24 w-16 shrink-0 overflow-hidden rounded-lg">
                {movie.posterUrl ? (
                  <img src={movie.posterUrl} alt={movie.title} className="h-full w-full object-cover" />
                ) : (
                  <PosterPlaceholder compact className="bg-gradient-to-br from-indigo-600 to-purple-700" />
                )}
              </div>
              <div>
                <h1 className="text-xl font-bold">{movie.title}</h1>
                <p className="mt-1 text-sm text-gray-400">
                  {['새싹시네마', theater, movie.runtimeMinutes ? `${movie.runtimeMinutes}분` : null, movie.ageRating]
                    .filter(Boolean)
                    .join(' · ')}
                </p>
              </div>
            </div>

            {!hasScreenings && (
              <p className="mt-6 text-sm text-gray-400">이번 주에는 이 영화의 상영 일정이 없습니다.</p>
            )}

            {hasScreenings && (
              <>
                <div className="grid grid-cols-1 gap-6 border-b border-white/10 py-6 sm:grid-cols-3">
                  <div>
                    <h2 className="mb-2 text-sm font-semibold text-gray-400">상영관</h2>
                    <div className="flex flex-wrap gap-2">
                      {theaterOptions.map((name) => (
                        <PillButton key={name} active={theater === name} onClick={() => setTheater(name)}>
                          {name}
                        </PillButton>
                      ))}
                    </div>
                  </div>

                  <div>
                    <h2 className="mb-2 text-sm font-semibold text-gray-400">날짜</h2>
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
                    <h2 className="mb-2 text-sm font-semibold text-gray-400">회차</h2>
                    <div className="flex flex-wrap gap-2">
                      {showtimeOptions.length === 0 && (
                        <p className="text-sm text-gray-500">선택한 날짜에 상영 회차가 없습니다.</p>
                      )}
                      {showtimeOptions.map((time) => (
                        <PillButton key={time} active={showtime === time} onClick={() => setShowtime(time)}>
                          {time}
                        </PillButton>
                      ))}
                    </div>
                  </div>
                </div>

                <div
                  className={`border-b border-white/10 py-6 ${showPaymentWidget ? 'pointer-events-none opacity-60' : ''}`}
                >
                  <div className="mb-3 flex items-center gap-2">
                    <h2 className="text-sm font-semibold text-gray-400">인원 선택</h2>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                        holidayPricing ? 'bg-rose-500/20 text-rose-300' : 'bg-indigo-500/20 text-indigo-300'
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
                            disallowed ? 'border-white/5 bg-slate-900/50 opacity-60' : 'border-white/10'
                          }`}
                        >
                          <div>
                            <p className="text-sm font-semibold text-gray-100">{category.label}</p>
                            <p className="text-xs text-gray-400">{category.sublabel}</p>
                            {disallowed ? (
                              <p className="mt-0.5 text-xs font-medium text-rose-400">
                                {movie.ageRating} 영화라 선택할 수 없어요
                              </p>
                            ) : (
                              <p className="mt-0.5 text-xs text-gray-400">
                                {priceFor(category.key, showDate, showtime).toLocaleString()}원
                              </p>
                            )}
                          </div>
                          <div className="flex items-center gap-3">
                            <button
                              type="button"
                              disabled={disallowed || showPaymentWidget}
                              onClick={() => updateTicketCount(category.key, -1)}
                              className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-800 text-gray-300 hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              -
                            </button>
                            <span className="w-4 text-center text-sm font-semibold">{ticketCounts[category.key]}</span>
                            <button
                              type="button"
                              disabled={disallowed || showPaymentWidget}
                              onClick={() => updateTicketCount(category.key, 1)}
                              className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-800 text-gray-300 hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              +
                            </button>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                  {totalTickets > 0 && (
                    <p className="mt-3 text-xs text-gray-400">
                      총 {totalTickets}명 · 좌석을 {totalTickets}석 선택해주세요.
                    </p>
                  )}
                </div>

                <div className={`py-8 ${showPaymentWidget ? 'pointer-events-none opacity-60' : ''}`}>
                  <SeatMap seats={seats} selectedSeats={selectedSeats} onToggleSeat={toggleSeat} />
                </div>

                <div className="flex flex-col gap-4 border-t border-white/10 pt-6 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <p className="text-sm text-gray-400">
                      선택 좌석:{' '}
                      {selectedSeatLabels.length > 0 ? (
                        <span className="font-semibold text-indigo-400">
                          {selectedSeatLabels.join(', ')} ({selectedSeatLabels.length}석)
                        </span>
                      ) : (
                        '없음'
                      )}
                    </p>
                    {totalTickets === 0 && <p className="mt-1 text-xs text-rose-400">인원을 먼저 선택해주세요.</p>}
                    {totalTickets > 0 && !seatsMatchTickets && (
                      <p className="mt-1 text-xs text-rose-400">
                        선택한 인원({totalTickets}명)만큼 좌석을 선택해주세요.
                      </p>
                    )}
                  </div>
                  <p className="text-xl font-bold">{totalPrice.toLocaleString()}원</p>
                  {!showPaymentWidget && (
                    <button
                      type="button"
                      disabled={!seatsMatchTickets}
                      onClick={handleProceedToPayment}
                      className="rounded-lg bg-indigo-600 px-6 py-3 text-sm font-semibold text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:bg-slate-700 disabled:text-gray-500"
                    >
                      결제하기
                    </button>
                  )}
                </div>

                {showPaymentWidget && (
                  <div className="mt-6 border-t border-white/10 pt-6">
                    <TossPaymentWidget amount={totalPrice} onComplete={handlePaymentComplete} />
                  </div>
                )}
              </>
            )}
          </>
        )}
      </div>

      {pendingWheelchairSeat && (
        <ConfirmDialog
          title="장애인석을 선택했습니다"
          message="장애인석을 선택하시겠습니까?"
          confirmLabel="선택"
          onConfirm={confirmWheelchairSeat}
          onCancel={() => setPendingWheelchairSeat(null)}
        />
      )}

      {showTeenAgeConfirm && (
        <ConfirmDialog
          title="관람 연령 확인"
          message={'해당 영화는 15세 이상 관람가입니다.\n만 12세~만 14세는 관람할 수 없습니다.'}
          confirmLabel="확인"
          onConfirm={confirmTeenAgeIncrease}
          onCancel={() => setShowTeenAgeConfirm(false)}
        />
      )}

      {showPaymentConfirm && (
        <ConfirmDialog
          title="결제 확인"
          message={'선택한 좌석은 결제 후에는 변경할 수 없습니다.\n결제하시겠습니까?'}
          confirmLabel="결제하기"
          onConfirm={() => {
            setShowPaymentConfirm(false)
            setShowPaymentWidget(true)
          }}
          onCancel={() => setShowPaymentConfirm(false)}
        />
      )}

      {resultDialog && (
        <InfoDialog
          title={resultDialog.title}
          message={resultDialog.message}
          tone={resultDialog.tone}
          onClose={() => setResultDialog(null)}
        />
      )}
    </div>
  )
}

export default BookingSeatPage
