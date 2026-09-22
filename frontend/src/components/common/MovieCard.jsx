import { Link } from 'react-router-dom'
import { getReviews } from '../../reviews/reviewsStorage'

function getAverageScore(movieId) {
  const scores = getReviews(movieId)
    .map((review) => review.score)
    .filter((score) => typeof score === 'number')
  if (scores.length === 0) return null
  return scores.reduce((sum, score) => sum + score, 0) / scores.length
}

function MovieCard({ movie }) {
  const { id, title, year, genres, ageRating, posterUrl } = movie
  const genreLabel = genres?.join(', ')
  const averageScore = getAverageScore(id)

  return (
    <Link to={`/movies/${id}`} className="group block shrink-0 w-full">
      <div className="relative aspect-[3/4] w-full overflow-hidden rounded-lg bg-slate-800 transition-transform group-hover:-translate-y-1">
        {posterUrl ? (
          <img src={posterUrl} alt={title} loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <div className="h-full w-full bg-gradient-to-br from-slate-700 to-slate-900" />
        )}
        {ageRating && (
          <span className="absolute top-2 left-2 max-w-[65%] rounded-md bg-black/60 px-2 py-1 text-[11px] font-semibold leading-tight text-gray-200">
            {ageRating}
          </span>
        )}
        {averageScore != null && (
          <span className="absolute top-2 right-2 flex items-center gap-1 rounded-md bg-black/60 px-2 py-1 text-xs font-semibold text-amber-400">
            ★ {averageScore.toFixed(1)}
          </span>
        )}
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
