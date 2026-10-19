import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { useWishlist } from '../../wishlist/WishlistContext'
import { getWishlist } from '../../api/wishlistApi'
import { getWatched } from '../../api/watchedApi'
import { cancelBooking, getMyBookings } from '../../api/bookingApi'
import { deleteReview, getMyReviews, updateReview } from '../../api/reviewApi'
import { changePassword, deleteAccount, getPreferredGenres, updatePreferredGenres } from '../../api/userApi'
import { getGenres, getMovieDetail } from '../../api/movieApi'
import { genreLabel } from '../MovieList/components/filterOptions'
import { AGE_CATEGORIES } from '../Booking/bookingData'
import MovieCard from '../../components/common/MovieCard'
import WriteReviewDialog from '../MovieDetail/components/WriteReviewDialog'
import AiTasteReport from './components/AiTasteReport'
import ProfileHeader from './components/ProfileHeader'
import BookingCalendar from './components/BookingCalendar'
import BookingSeatMapDialog from './components/BookingSeatMapDialog'
import ChangePasswordDialog from './components/ChangePasswordDialog'
import DeleteAccountDialog from './components/DeleteAccountDialog'
import ConfirmDialog from '../../components/common/ConfirmDialog'
import InfoDialog from '../../components/common/InfoDialog'

const TABS = ['예매 내역', '내가 쓴 리뷰', '찜한 영화', '시청완료']

function formatJoinedAt(createdAt) {
  if (!createdAt) return '-'
  const date = new Date(createdAt)
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${yyyy}.${mm}.${dd}`
}

// 상영이 이미 끝난 예매는 좌석 정보는 그대로 보여주되, 눌러서 좌석 배치도/변경 다이얼로그를 열 수는
// 없게 합니다(이미 끝난 상영의 좌석을 바꾸는 건 의미가 없으니까요). "상영 예정/상영 종료" 탭 구분과
// 예매 통계도 이 기준(실제로 상영이 시작됐는지)을 그대로 씁니다.
function isPastShowing(booking) {
  if (!booking.showDate || !booking.showtime) return false
  return showingDateTime(booking) < new Date()
}

// 취소/좌석변경 가능 여부 전용 - 백엔드(BookingService.BOOKING_CUTOFF_MINUTES)와 같은 기준으로,
// 아직 상영 전이라도 시작 30분 이내면 버튼을 눌러도 서버가 거절하므로 미리 숨겨서 혼란을 줄입니다.
// isPastShowing(실제 상영 시작 여부)과는 다른 기준이라 따로 둡니다.
const BOOKING_CANCEL_CUTOFF_MINUTES = 30

function isPastCancelCutoff(booking) {
  if (!booking.showDate || !booking.showtime) return false
  return showingDateTime(booking) < new Date(Date.now() + BOOKING_CANCEL_CUTOFF_MINUTES * 60 * 1000)
}

function showingDateTime(booking) {
  if (!booking.showDate || !booking.showtime) return new Date(0)
  return new Date(`${booking.showDate}T${booking.showtime}`)
}

// 상영 시작 시각으로 조조/일반/심야를 가립니다 - 백엔드 TicketPricing.timePeriodFor()와 경계값을
// 맞춰뒀습니다(10시/21시 "경계"는 각각 일반/심야 쪽에 포함). 요금표도 이 구분을 기준으로 달라집니다.
function timePeriodLabel(showtime) {
  if (!showtime) return ''
  const hour = Number(showtime.slice(0, showtime.indexOf(':')))
  if (hour < 10) return '조조'
  if (hour < 21) return '일반'
  return '심야'
}

// 예매 내역의 인원 구분별 인원 수("성인 2명 · 청소년 1명")를 표시 순서(성인→청소년→어린이→우대)대로
// 만듭니다. 0명인 구분은 ticketCounts에 아예 없으니 자연스럽게 빠집니다.
function formatTicketCounts(ticketCounts) {
  if (!ticketCounts) return ''
  return AGE_CATEGORIES.map(({ key, label }) => {
    const count = ticketCounts[key]
    return count > 0 ? `${label} ${count}명` : null
  })
    .filter(Boolean)
    .join(' · ')
}

function formatShortDate(isoString) {
  if (!isoString) return ''
  const date = new Date(isoString)
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

// 실제 감상 패턴 분석 대신, 사용자가 직접 고른 선호 장르를 그대로 요약해 보여줍니다(가짜 퍼센트 수치는 넣지 않음).
function buildTasteReportText(genres) {
  if (genres.length === 0) {
    return '아직 선호 장르를 선택하지 않으셨어요. 아래에서 좋아하는 장르를 선택하면 홈 화면 추천에 반영돼요.'
  }
  return `선호 장르로 ${genres.map(genreLabel).join(', ')}을(를) 선택하셨어요. 홈 화면의 "취향저격 신작"에서 관련 영화를 우선적으로 보여드릴게요.`
}

// 찜한 영화/시청완료에는 movieId·movieTitle·posterUrl만 저장돼 있어서, 목록/검색과 같은 카드 형태(연령등급·평점
// 배지, 장르)로 보여주려면 KMDB 상세를 다시 조회해 MovieCard가 기대하는 모양으로 채워야 합니다.
function enrichAsMovie(item) {
  return getMovieDetail(item.movieId)
    .then((detail) => ({
      id: item.movieId,
      title: detail.title,
      year: detail.year,
      genres: detail.genres,
      ageRating: detail.ageRating,
      posterUrl: detail.posterUrl ?? item.posterUrl,
      averageScore: detail.averageScore,
    }))
    .catch(() => ({
      id: item.movieId,
      title: item.movieTitle,
      year: null,
      genres: [],
      ageRating: null,
      posterUrl: item.posterUrl,
      averageScore: null,
    }))
}

function MoviePosterGrid({ movies, isLoading, emptyMessage }) {
  if (movies.length === 0) {
    return <p className="text-sm text-gray-500">{isLoading ? '불러오는 중...' : emptyMessage}</p>
  }
  return (
    <div className="grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-5">
      {movies.map((movie) => (
        <MovieCard key={movie.id} movie={movie} />
      ))}
    </div>
  )
}

function PreferredGenresCard({ genres, selected, onToggle, onSave, saving }) {
  return (
    <div className="mt-4 rounded-lg bg-slate-900 p-4">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-bold text-white">선호 장르</h2>
        <button
          type="button"
          onClick={onSave}
          disabled={saving}
          className="rounded-md bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? '저장 중...' : '저장'}
        </button>
      </div>
      <p className="mt-1 text-xs text-gray-500">좋아하는 장르를 선택해두면 AI 추천이 더 정확해져요.</p>
      <div className="mt-3 flex flex-wrap gap-2">
        {genres.map((genre) => {
          const isSelected = selected.includes(genre)
          return (
            <button
              key={genre}
              type="button"
              onClick={() => onToggle(genre)}
              className={`rounded-full px-3 py-1.5 text-xs font-semibold ${
                isSelected ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-gray-300 hover:bg-slate-700'
              }`}
            >
              {genreLabel(genre)}
            </button>
          )
        })}
      </div>
    </div>
  )
}

function SavedGenresDialog({ onClose }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-sm rounded-xl bg-slate-900 p-6 text-center">
        <p className="text-sm font-semibold text-white">저장되었습니다</p>
        <button
          type="button"
          onClick={onClose}
          className="mt-4 w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
        >
          확인
        </button>
      </div>
    </div>
  )
}

const BOOKING_FILTERS = ['상영 예정', '상영 종료']

// "상영 종료" 예매를 월별로 골라 보기 위한 선택지 - 실제로 종료된 예매가 있는 달만, 최근 달부터 만듭니다.
// value는 상영일의 "YYYY-MM" 앞부분이라 booking.showDate.startsWith(value)로 바로 거를 수 있습니다.
function buildMonthOptions(pastBookings) {
  const counts = new Map()
  for (const booking of pastBookings) {
    if (!booking.showDate) continue
    const key = booking.showDate.slice(0, 7)
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  return [...counts.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([value, count]) => ({
      value,
      count,
      label: `${value.slice(0, 4)}년 ${Number(value.slice(5, 7))}월`,
    }))
}

function BookingMonthSelect({ options, totalCount, value, onChange }) {
  return (
    <select
      value={value}
      onChange={(event) => onChange(event.target.value)}
      aria-label="상영 종료 예매 월 선택"
      className="rounded-md bg-slate-800 px-3 py-1.5 text-xs font-semibold text-gray-300 hover:bg-slate-700"
    >
      <option value="">전체 ({totalCount}건)</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label} ({option.count}건)
        </option>
      ))}
    </select>
  )
}

function BookingFilterTabs({ active, onChange }) {
  return (
    <div className="flex gap-2">
      {BOOKING_FILTERS.map((filter) => (
        <button
          key={filter}
          type="button"
          onClick={() => onChange(filter)}
          className={`rounded-full px-3 py-1.5 text-xs font-semibold ${
            active === filter ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-gray-300 hover:bg-slate-700'
          }`}
        >
          {filter}
        </button>
      ))}
    </div>
  )
}

const BOOKING_VIEWS = ['달력', '목록']

// 예매 내역을 달력으로 볼지 목록으로 볼지 고릅니다(달력이 기본).
function BookingViewToggle({ active, onChange }) {
  return (
    <div className="flex rounded-full bg-slate-900 p-0.5">
      {BOOKING_VIEWS.map((view) => (
        <button
          key={view}
          type="button"
          onClick={() => onChange(view)}
          aria-pressed={active === view}
          className={`rounded-full px-3 py-1 text-xs font-semibold ${
            active === view ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-gray-200'
          }`}
        >
          {view}
        </button>
      ))}
    </div>
  )
}

// 달력을 처음 열 때 보여줄 달 - 가장 임박한 상영 예정 예매가 있는 달, 없으면 가장 최근에 본(끝난) 예매의 달,
// 예매가 하나도 없으면 이번 달입니다. showDate("YYYY-MM-DD")를 문자열로 잘라 쓰는 이유는 시간대 때문에
// Date로 바꾸면 달 경계에서 하루씩 밀릴 수 있어서입니다.
function defaultCalendarMonth(bookings) {
  const dated = bookings.filter((booking) => booking.showDate)
  const upcoming = dated
    .filter((booking) => !isPastShowing(booking))
    .sort((a, b) => showingDateTime(a) - showingDateTime(b))[0]
  const latest = [...dated].sort((a, b) => showingDateTime(b) - showingDateTime(a))[0]
  const target = upcoming ?? latest
  if (target) {
    const [year, month] = target.showDate.split('-').map(Number)
    return { year, monthIndex: month - 1 }
  }
  const now = new Date()
  return { year: now.getFullYear(), monthIndex: now.getMonth() }
}

function MyBookingList({ bookings, isLoading, onSeatClick, onCancel, emptyMessage }) {
  if (isLoading) {
    return <p className="text-sm text-gray-500">불러오는 중...</p>
  }
  if (bookings.length === 0) {
    return <p className="text-sm text-gray-500">{emptyMessage}</p>
  }
  return (
    <div className="space-y-3">
      {bookings.map((booking) => {
        const isPast = isPastShowing(booking)
        // 상영이 아직 시작 전이라도 30분 이내로 임박했으면 취소/좌석변경 버튼을 숨깁니다(서버도 같은
        // 기준으로 거절하므로, 눌러보고 나서야 에러를 보게 하지 않습니다).
        const canModify = !isPastCancelCutoff(booking)
        return (
          <div key={booking.id} className="rounded-lg bg-slate-900 p-4">
            <div className="flex items-center justify-between">
              {booking.movieId ? (
                <Link
                  to={`/movies/${booking.movieId}`}
                  className="text-sm font-semibold text-gray-200 hover:text-white hover:underline"
                >
                  {booking.movieTitle}
                </Link>
              ) : (
                <span className="text-sm font-semibold text-gray-200">{booking.movieTitle}</span>
              )}
              <span className="text-xs text-gray-500">{formatShortDate(booking.createdAt)} 예매</span>
            </div>
            <p className="mt-1 text-xs text-gray-400">
              {booking.theaterName} · {booking.showDate} {booking.showtime}
              {timePeriodLabel(booking.showtime) && (
                <span className="ml-1 rounded bg-slate-800 px-1.5 py-0.5 text-[11px] font-semibold text-indigo-300">
                  {timePeriodLabel(booking.showtime)}
                </span>
              )}
              {isPast && <span className="ml-2 text-gray-600">(상영 종료)</span>}
              {!isPast && !canModify && (
                <span className="ml-2 text-amber-500">(상영 임박 · 취소/좌석변경 불가)</span>
              )}
            </p>
            {formatTicketCounts(booking.ticketCounts) && (
              <p className="mt-1 text-xs text-gray-400">{formatTicketCounts(booking.ticketCounts)}</p>
            )}
            <div className="mt-2 flex items-center justify-between text-sm">
              <span className="flex flex-wrap items-center gap-x-1 text-gray-300">
                좌석{' '}
                {booking.seats.map((seat, index) => (
                  <span key={seat}>
                    {canModify ? (
                      <button
                        type="button"
                        onClick={() => onSeatClick(booking)}
                        className="font-semibold text-indigo-300 hover:text-indigo-200 hover:underline"
                      >
                        {seat}
                      </button>
                    ) : (
                      <span className="font-semibold text-gray-400">{seat}</span>
                    )}
                    {index < booking.seats.length - 1 && ','}
                  </span>
                ))}
              </span>
              <span className="font-semibold text-white">{booking.totalPrice.toLocaleString()}원</span>
            </div>
            {canModify && (
              <div className="mt-2 flex justify-end">
                <button
                  type="button"
                  onClick={() => onCancel(booking)}
                  className="rounded-md bg-slate-800 px-3 py-1.5 text-xs font-semibold text-gray-300 hover:bg-rose-600 hover:text-white"
                >
                  예매 취소
                </button>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

function MyReviewList({ reviews, onEdit, onDelete }) {
  if (reviews.length === 0) {
    return <p className="text-sm text-gray-500">아직 작성한 리뷰가 없습니다.</p>
  }
  return (
    <div className="space-y-3">
      {reviews.map((review) => (
        <div key={review.id} className="rounded-lg bg-slate-900 p-4">
          <div className="flex items-center justify-between">
            <Link
              to={`/movies/${review.movieId}`}
              className="text-sm font-semibold text-gray-200 hover:text-white hover:underline"
            >
              {review.movieTitle}
            </Link>
            <span className="text-xs text-gray-500">{formatShortDate(review.createdAt)}</span>
          </div>
          <div className="mt-1 flex items-center justify-between">
            <span className="text-amber-400">
              {'★'.repeat(review.score)}
              <span className="text-gray-600">{'★'.repeat(5 - review.score)}</span>
            </span>
            <div className="flex gap-2 text-xs text-gray-400">
              <button type="button" onClick={() => onEdit(review)} className="hover:text-white">
                수정
              </button>
              <button type="button" onClick={() => onDelete(review)} className="hover:text-red-400">
                삭제
              </button>
            </div>
          </div>
          <p className="mt-2 text-sm text-gray-300">{review.content}</p>
        </div>
      ))}
    </div>
  )
}

function MyPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { wishlistedIds, refresh: refreshWishlistedIds } = useWishlist()
  const [activeTab, setActiveTab] = useState(TABS[0])
  const [bookingFilter, setBookingFilter] = useState(BOOKING_FILTERS[0])
  const [bookingView, setBookingView] = useState(BOOKING_VIEWS[0])
  const [pastMonth, setPastMonth] = useState('') // 목록 > 상영 종료에서 고른 월("YYYY-MM"), ''이면 전체
  const [calendarMonth, setCalendarMonth] = useState(null) // null 이면 defaultCalendarMonth 를 씁니다
  const [selectedDate, setSelectedDate] = useState(null) // 달력에서 고른 날짜("YYYY-MM-DD"), null 이면 그 달 전체
  const [bookings, setBookings] = useState([])
  const [bookingsLoading, setBookingsLoading] = useState(true)
  const [wishlist, setWishlist] = useState([])
  const [watched, setWatched] = useState([])
  const [wishlistMovies, setWishlistMovies] = useState([])
  const [watchedMovies, setWatchedMovies] = useState([])
  const [myReviews, setMyReviews] = useState([])
  const [editingReview, setEditingReview] = useState(null)
  const [preferredGenres, setPreferredGenres] = useState([])
  const [savedGenres, setSavedGenres] = useState([])
  const [savingGenres, setSavingGenres] = useState(false)
  const [showSavedDialog, setShowSavedDialog] = useState(false)
  const [seatMapBooking, setSeatMapBooking] = useState(null)
  const [showChangePassword, setShowChangePassword] = useState(false)
  const [showDeleteAccount, setShowDeleteAccount] = useState(false)
  // 선호 장르 선택지 - 백엔드 MovieGenres.ALL이 유일한 출처라 하드코딩해두지 않고 받아옵니다
  // ("전체"는 필터 사이드바 전용 옵션이라 여긴 받아온 목록 그대로 씁니다).
  const [selectableGenres, setSelectableGenres] = useState([])

  const refreshMyReviews = () => {
    getMyReviews(user.id).then(setMyReviews)
  }

  // 좌석변경에 성공하면 예매 내역의 좌석 번호 표시도 최신 상태로 맞춥니다.
  const refreshBookings = () => {
    getMyBookings(user.id).then(setBookings)
  }

  useEffect(() => {
    let cancelled = false

    // AI 채팅으로 찜이 추가되는 경우처럼, 로그인 시 한 번 불러온 뒤로 어긋날 수 있는 전역 찜 상태를
    // 마이페이지에 들어올 때마다 서버 기준으로 다시 맞춥니다(안 그러면 방금 든 getWishlist 결과는 맞아도
    // 아래 visibleWishlistMovies 필터가 옛 상태로 걸러내 화면엔 안 보이는 문제가 있었습니다).
    refreshWishlistedIds()

    getGenres()
      .then((list) => {
        if (!cancelled) setSelectableGenres(list)
      })
      .catch(() => {})

    getMyBookings(user.id)
      .then((bookingList) => {
        if (cancelled) return
        setBookings(bookingList)
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setBookingsLoading(false)
      })

    Promise.all([getWishlist(user.id), getWatched(user.id), getMyReviews(user.id), getPreferredGenres(user.id)])
      .then(([wishlistList, watchedList, reviewList, preferredGenresDto]) => {
        if (cancelled) return
        setWishlist(wishlistList)
        setWatched(watchedList)
        setMyReviews(reviewList)
        setPreferredGenres(preferredGenresDto.genres)
        setSavedGenres(preferredGenresDto.genres)

        Promise.all([
          Promise.all(wishlistList.map(enrichAsMovie)),
          Promise.all(watchedList.map(enrichAsMovie)),
        ]).then(([wishlistMoviesResult, watchedMoviesResult]) => {
          if (cancelled) return
          setWishlistMovies(wishlistMoviesResult)
          setWatchedMovies(watchedMoviesResult)
        })
      })
      .catch(() => {})

    return () => {
      cancelled = true
    }
  }, [user.id, refreshWishlistedIds])

  const handleToggleGenre = (genre) => {
    setPreferredGenres((prev) =>
      prev.includes(genre) ? prev.filter((g) => g !== genre) : [...prev, genre],
    )
  }

  const handleSaveGenres = async () => {
    setSavingGenres(true)
    try {
      const dto = await updatePreferredGenres(user.id, preferredGenres)
      setPreferredGenres(dto.genres)
      setSavedGenres(dto.genres)
      setShowSavedDialog(true)
    } catch (error) {
      window.alert(error.message)
    } finally {
      setSavingGenres(false)
    }
  }

  const handleChangePassword = ({ currentPassword, newPassword }) =>
    changePassword(user.id, { currentPassword, newPassword })

  const [pendingCancelBooking, setPendingCancelBooking] = useState(null)
  const [cancelErrorMessage, setCancelErrorMessage] = useState('')

  const handleCancelBooking = (booking) => {
    setPendingCancelBooking(booking)
  }

  const confirmCancelBooking = async () => {
    const booking = pendingCancelBooking
    setPendingCancelBooking(null)
    try {
      await cancelBooking(user.id, booking.id)
      refreshBookings()
    } catch (error) {
      setCancelErrorMessage(error.message)
    }
  }

  const handleDeleteAccount = async (password) => {
    await deleteAccount(user.id, password)
    logout()
    navigate('/login', { replace: true })
  }

  const handleReviewEdit = (review) => {
    setEditingReview(review)
  }

  const handleReviewEditSubmit = async ({ score, content }) => {
    try {
      await updateReview(editingReview.movieId, editingReview.id, user.id, {
        movieTitle: editingReview.movieTitle,
        score,
        content,
      })
      refreshMyReviews()
      setEditingReview(null)
    } catch (error) {
      window.alert(error.message)
    }
  }

  const handleReviewDelete = async (review) => {
    if (!window.confirm('이 리뷰를 삭제할까요?')) return
    try {
      await deleteReview(review.movieId, review.id, user.id)
      refreshMyReviews()
    } catch (error) {
      window.alert(error.message)
    }
  }

  // 찜 목록 개수/카드 목록은 WishlistContext(실시간 찜 설정/해제 상태)를 기준으로 다시 걸러냅니다.
  const visibleWishlistMovies = wishlistMovies.filter((movie) => wishlistedIds.has(movie.id))

  // 예매 내역을 "상영 예정"/"상영 종료"로 나눠서 보여주고, 상영 날짜·시간 순으로 정렬합니다 - 상영
  // 예정은 가장 임박한 회차가 먼저 보이도록 오름차순, 상영 종료는 가장 최근에 끝난 회차가 먼저 보이도록
  // 내림차순입니다.
  // 상영 종료 탭에서는 월별로도 걸러 볼 수 있습니다. 고른 월에 더는 예매가 없으면(목록이 바뀐 경우) 전체로 돌아갑니다.
  const pastBookings = bookings.filter(isPastShowing)
  const pastMonthOptions = buildMonthOptions(pastBookings)
  const activePastMonth = pastMonthOptions.some((option) => option.value === pastMonth) ? pastMonth : ''
  const filteredBookings = bookings
    .filter((booking) => (bookingFilter === '상영 종료' ? isPastShowing(booking) : !isPastShowing(booking)))
    .filter(
      (booking) => bookingFilter !== '상영 종료' || !activePastMonth || booking.showDate?.startsWith(activePastMonth),
    )
    .sort((a, b) => {
      const diff = showingDateTime(a) - showingDateTime(b)
      return bookingFilter === '상영 종료' ? -diff : diff
    })
  const bookingEmptyMessage =
    bookingFilter === '상영 종료' ? '상영 종료된 예매 내역이 없습니다.' : '상영 예정인 예매 내역이 없습니다.'

  // 달력 보기: 달력은 상영 예정/종료를 모두 표시하고, 아래 목록은 고른 날짜(없으면 보고 있는 달 전체)의 예매를
  // 상영 시각 순으로 보여줍니다. 달력의 달/날짜를 바꾸면 이 목록도 같이 바뀝니다.
  const activeMonth = calendarMonth ?? defaultCalendarMonth(bookings)
  const monthPrefix = `${activeMonth.year}-${String(activeMonth.monthIndex + 1).padStart(2, '0')}-`
  const calendarListBookings = bookings
    .filter((booking) =>
      selectedDate ? booking.showDate === selectedDate : booking.showDate?.startsWith(monthPrefix),
    )
    .sort((a, b) => showingDateTime(a) - showingDateTime(b))
  const calendarListTitle = selectedDate
    ? `${Number(selectedDate.slice(5, 7))}월 ${Number(selectedDate.slice(8, 10))}일 예매 내역`
    : `${activeMonth.monthIndex + 1}월 예매 내역`
  const calendarEmptyMessage = selectedDate
    ? '이 날짜에는 예매 내역이 없습니다.'
    : `${activeMonth.monthIndex + 1}월에는 예매 내역이 없습니다.`

  const handleChangeMonth = (delta) => {
    const next = new Date(activeMonth.year, activeMonth.monthIndex + delta, 1)
    setCalendarMonth({ year: next.getFullYear(), monthIndex: next.getMonth() })
    setSelectedDate(null)
  }
  const handleGoToday = () => {
    const now = new Date()
    setCalendarMonth({ year: now.getFullYear(), monthIndex: now.getMonth() })
    setSelectedDate(null)
  }

  const profile = {
    nickname: user.nickname,
    joinedAt: formatJoinedAt(user.createdAt),
    // 접속 시점 기준으로 이미 상영이 끝난 예매는 "현재 예매"로 보지 않고 제외합니다.
    bookingCount: bookings.filter((booking) => !isPastShowing(booking)).length,
    reviewCount: myReviews.length,
    wishlistCount: wishlistedIds.size,
    watchedCount: watched.length,
  }

  return (
    <div className="mx-auto max-w-6xl px-6 py-6">
      <ProfileHeader
        user={profile}
        onChangePassword={() => setShowChangePassword(true)}
        onDeleteAccount={() => setShowDeleteAccount(true)}
      />

      {showChangePassword && (
        <ChangePasswordDialog onClose={() => setShowChangePassword(false)} onSubmit={handleChangePassword} />
      )}

      {showDeleteAccount && (
        <DeleteAccountDialog onClose={() => setShowDeleteAccount(false)} onSubmit={handleDeleteAccount} />
      )}

      <AiTasteReport text={buildTasteReportText(savedGenres)} />

      <PreferredGenresCard
        genres={selectableGenres}
        selected={preferredGenres}
        onToggle={handleToggleGenre}
        onSave={handleSaveGenres}
        saving={savingGenres}
      />

      <div className="mt-8 flex gap-6 overflow-x-auto border-b border-white/5 text-sm font-medium text-gray-400">
        {TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            className={`-mb-px shrink-0 whitespace-nowrap border-b-2 pb-3 ${
              activeTab === tab
                ? 'border-indigo-500 text-white'
                : 'border-transparent hover:text-gray-200'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      <div className="mt-6">
        {activeTab === '예매 내역' && (
          <>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
              {bookingView === '목록' ? (
                <div className="flex flex-wrap items-center gap-2">
                  <BookingFilterTabs active={bookingFilter} onChange={setBookingFilter} />
                  {bookingFilter === '상영 종료' && pastMonthOptions.length > 0 && (
                    <BookingMonthSelect
                      options={pastMonthOptions}
                      totalCount={pastBookings.length}
                      value={activePastMonth}
                      onChange={setPastMonth}
                    />
                  )}
                </div>
              ) : (
                <span />
              )}
              <BookingViewToggle active={bookingView} onChange={setBookingView} />
            </div>
            {bookingView === '달력' ? (
              <>
                <BookingCalendar
                  bookings={bookings}
                  year={activeMonth.year}
                  monthIndex={activeMonth.monthIndex}
                  selectedDate={selectedDate}
                  isPast={isPastShowing}
                  onSelectDate={setSelectedDate}
                  onChangeMonth={handleChangeMonth}
                  onGoToday={handleGoToday}
                />
                <h3 className="mb-3 mt-6 text-sm font-bold text-white">
                  {calendarListTitle}
                  {!bookingsLoading && <span className="ml-2 text-gray-500">{calendarListBookings.length}건</span>}
                </h3>
                <MyBookingList
                  bookings={calendarListBookings}
                  isLoading={bookingsLoading}
                  onSeatClick={setSeatMapBooking}
                  onCancel={handleCancelBooking}
                  emptyMessage={calendarEmptyMessage}
                />
              </>
            ) : (
              <MyBookingList
                bookings={filteredBookings}
                isLoading={bookingsLoading}
                onSeatClick={setSeatMapBooking}
                onCancel={handleCancelBooking}
                emptyMessage={bookingEmptyMessage}
              />
            )}
          </>
        )}
        {activeTab === '찜한 영화' && (
          <MoviePosterGrid
            movies={visibleWishlistMovies}
            isLoading={wishlist.length > 0 && wishlistMovies.length === 0}
            emptyMessage="찜한 영화가 아직 없습니다."
          />
        )}
        {activeTab === '시청완료' && (
          <MoviePosterGrid
            movies={watchedMovies}
            isLoading={watched.length > 0 && watchedMovies.length === 0}
            emptyMessage="시청완료로 표시한 영화가 아직 없습니다."
          />
        )}
        {activeTab === '내가 쓴 리뷰' && (
          <MyReviewList reviews={myReviews} onEdit={handleReviewEdit} onDelete={handleReviewDelete} />
        )}
      </div>

      {editingReview && (
        <WriteReviewDialog
          movieTitle={editingReview.movieTitle}
          initialReview={editingReview}
          onSubmit={handleReviewEditSubmit}
          onClose={() => setEditingReview(null)}
        />
      )}

      {showSavedDialog && <SavedGenresDialog onClose={() => setShowSavedDialog(false)} />}

      {seatMapBooking && (
        <BookingSeatMapDialog
          booking={seatMapBooking}
          userId={user.id}
          onClose={() => setSeatMapBooking(null)}
          onSeatChanged={refreshBookings}
        />
      )}

      {pendingCancelBooking && (
        <ConfirmDialog
          title="예매 취소"
          message={'이 예매를 취소하시겠습니까?\n취소하면 되돌릴 수 없습니다.'}
          confirmLabel="취소하기"
          danger
          onConfirm={confirmCancelBooking}
          onCancel={() => setPendingCancelBooking(null)}
        />
      )}

      {cancelErrorMessage && (
        <InfoDialog title="예매 취소 실패" message={cancelErrorMessage} tone="error" onClose={() => setCancelErrorMessage('')} />
      )}
    </div>
  )
}

export default MyPage
