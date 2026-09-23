import { Link } from 'react-router-dom'
import { genreLabel as translateGenre } from '../../pages/MovieList/components/filterOptions'
import PosterPlaceholder from './PosterPlaceholder'

function MovieCard({ movie }) {
  const { id, title, year, genres, ageRating, posterUrl, averageScore } = movie
  const genreLabel = genres?.map(translateGenre).join(', ')

  return (
    <Link to={`/movies/${id}`} className="group block shrink-0 w-full">
      <div className="relative aspect-[3/4] w-full overflow-hidden rounded-lg bg-slate-800 transition-transform group-hover:-translate-y-1">
        {posterUrl ? (
          <img src={posterUrl} alt={title} loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <PosterPlaceholder />
        )}
        <span className="absolute top-2 left-2 max-w-[65%] rounded-md bg-black/60 px-2 py-1 text-[11px] font-semibold leading-tight text-gray-200">
          {ageRating ?? '정보 없음'}
        </span>
        <span
          className={`absolute top-2 right-2 flex items-center gap-1 rounded-md bg-black/60 px-2 py-1 text-xs font-semibold ${
            averageScore != null ? 'text-amber-400' : 'text-gray-400'
          }`}
        >
          {averageScore != null ? `★ ${averageScore.toFixed(1)}` : '정보 없음'}
        </span>
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
