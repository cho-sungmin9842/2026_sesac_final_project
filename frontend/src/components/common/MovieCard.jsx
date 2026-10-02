import { Link } from 'react-router-dom'
import { genreLabel as translateGenre } from '../../pages/MovieList/components/filterOptions'
import { useWishlist } from '../../wishlist/WishlistContext'
import PosterPlaceholder from './PosterPlaceholder'

function MovieCard({ movie }) {
  const { id, title, year, genres, ageRating, posterUrl, averageScore } = movie
  const genreLabel = genres?.map(translateGenre).join(', ')
  const { wishlistedIds, toggle } = useWishlist()
  const wishlisted = wishlistedIds.has(id)

  const handleToggleWishlist = (event) => {
    event.preventDefault()
    event.stopPropagation()
    toggle({ id, title, posterUrl })
  }

  return (
    <Link to={`/movies/${id}`} className="group block shrink-0 w-full">
      <div className="relative aspect-[3/4] w-full overflow-hidden rounded-lg bg-slate-800 transition-transform group-hover:-translate-y-1">
        {posterUrl ? (
          <img src={posterUrl} alt={title} loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <PosterPlaceholder />
        )}
        <div className="absolute inset-x-2 top-2 z-10 flex flex-col items-start gap-1.5">
          <div className="flex w-full items-start justify-between gap-1">
            <span className="max-w-[65%] rounded-md bg-black/60 px-2 py-1 text-[11px] font-semibold leading-tight text-gray-200">
              {ageRating ?? '정보 없음'}
            </span>
            <span
              className={`flex shrink-0 items-center gap-1 rounded-md bg-black/60 px-2 py-1 text-xs font-semibold ${
                averageScore != null ? 'text-amber-400' : 'text-gray-400'
              }`}
            >
              {averageScore != null ? `★ ${averageScore.toFixed(1)}` : '정보 없음'}
            </span>
          </div>
          {/* 위 배지가 줄바꿈돼 세로로 길어져도, 같은 flex-col 흐름 안에 있어 겹치지 않고 항상 그 아래로 밀려납니다. */}
          <button
            type="button"
            onClick={handleToggleWishlist}
            aria-label={wishlisted ? '찜 해제' : '찜하기'}
            aria-pressed={wishlisted}
            className={`flex h-7 w-7 items-center justify-center rounded-full bg-black/60 transition hover:bg-black/80 ${
              wishlisted ? 'text-amber-400' : 'text-gray-200'
            }`}
          >
            <svg viewBox="0 0 24 24" className="h-4 w-4" fill={wishlisted ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M6 3.75A1.75 1.75 0 0 1 7.75 2h8.5A1.75 1.75 0 0 1 18 3.75v17.5l-6-3.5-6 3.5V3.75Z"
              />
            </svg>
          </button>
        </div>
      </div>
      <p className="mt-2 truncate text-sm font-semibold text-gray-100">{title}</p>
      <p className="text-xs text-gray-400">
        {year}
        {genreLabel ? ` · ${genreLabel}` : ''}
      </p>
    </Link>
  )
}

export default MovieCard
