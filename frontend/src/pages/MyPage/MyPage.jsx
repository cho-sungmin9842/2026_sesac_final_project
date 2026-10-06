import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { useWishlist } from '../../wishlist/WishlistContext'
import { getWishlist } from '../../api/wishlistApi'
import { getWatched } from '../../api/watchedApi'
import { getMyBookings } from '../../api/bookingApi'
import { deleteReview, getMyReviews, updateReview } from '../../api/reviewApi'
import { getPreferredGenres, updatePreferredGenres } from '../../api/userApi'
import { getMovieDetail } from '../../api/movieApi'
import { genreLabel, genres as ALL_GENRES } from '../MovieList/components/filterOptions'
import MovieCard from '../../components/common/MovieCard'
import WriteReviewDialog from '../MovieDetail/components/WriteReviewDialog'
import AiTasteReport from './components/AiTasteReport'
import ProfileHeader from './components/ProfileHeader'
import BookingSeatMapDialog from './components/BookingSeatMapDialog'

const TABS = ['예매 내역', '찜한 영화', '시청완료', '내가 쓴 리뷰']
// "전체"는 필터 전용 옵션이라 개인 취향 선택지에서는 뺍니다.
const SELECTABLE_GENRES = ALL_GENRES.filter((genre) => genre !== '전체')

function formatJoinedAt(createdAt) {
  if (!createdAt) return '-'
  const date = new Date(createdAt)
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${yyyy}.${mm}.${dd}`
}

// 상영이 이미 끝난 예매는 좌석 정보는 그대로 보여주되, 눌러서 좌석 배치도/변경 다이얼로그를 열 수는
// 없게 합니다(이미 끝난 상영의 좌석을 바꾸는 건 의미가 없으니까요).
function isPastShowing(booking) {
  if (!booking.showDate || !booking.showtime) return false
  return new Date(`${booking.showDate}T${booking.showtime}`) < new Date()
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

function PreferredGenresCard({ selected, onToggle, onSave, saving }) {
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
        {SELECTABLE_GENRES.map((genre) => {
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

function MyBookingList({ bookings, isLoading, onSeatClick }) {
  if (isLoading) {
    return <p className="text-sm text-gray-500">불러오는 중...</p>
  }
  if (bookings.length === 0) {
    return <p className="text-sm text-gray-500">예매 내역이 아직 없습니다.</p>
  }
  return (
    <div className="space-y-3">
      {bookings.map((booking) => {
        const isPast = isPastShowing(booking)
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
              {isPast && <span className="ml-2 text-gray-600">(상영 종료)</span>}
            </p>
            <div className="mt-2 flex items-center justify-between text-sm">
              <span className="flex flex-wrap items-center gap-x-1 text-gray-300">
                좌석{' '}
                {booking.seats.map((seat, index) => (
                  <span key={seat}>
                    {isPast ? (
                      <span className="font-semibold text-gray-400">{seat}</span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => onSeatClick(booking)}
                        className="font-semibold text-indigo-300 hover:text-indigo-200 hover:underline"
                      >
                        {seat}
                      </button>
                    )}
                    {index < booking.seats.length - 1 && ','}
                  </span>
                ))}
              </span>
              <span className="font-semibold text-white">{booking.totalPrice.toLocaleString()}원</span>
            </div>
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
  const { user } = useAuth()
  const { wishlistedIds, refresh: refreshWishlistedIds } = useWishlist()
  const [activeTab, setActiveTab] = useState(TABS[0])
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

  const profile = {
    nickname: user.nickname,
    joinedAt: formatJoinedAt(user.createdAt),
    reviewCount: myReviews.length,
    wishlistCount: wishlistedIds.size,
    watchedCount: watched.length,
  }

  return (
    <div className="mx-auto max-w-6xl px-6 py-6">
      <ProfileHeader user={profile} />

      <AiTasteReport text={buildTasteReportText(savedGenres)} />

      <PreferredGenresCard
        selected={preferredGenres}
        onToggle={handleToggleGenre}
        onSave={handleSaveGenres}
        saving={savingGenres}
      />

      <div className="mt-8 flex gap-6 border-b border-white/5 text-sm font-medium text-gray-400">
        {TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            className={`-mb-px border-b-2 pb-3 ${
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
          <MyBookingList bookings={bookings} isLoading={bookingsLoading} onSeatClick={setSeatMapBooking} />
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
    </div>
  )
}

export default MyPage
