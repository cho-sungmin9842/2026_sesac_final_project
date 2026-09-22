import { Link } from 'react-router-dom'

function NowShowingCard({ movie }) {
  return (
    <Link
      to={`/booking/${movie.id}`}
      className="group block overflow-hidden rounded-lg bg-slate-900 transition-transform hover:-translate-y-1"
    >
      <div className="aspect-[3/4] w-full overflow-hidden bg-slate-800">
        {movie.posterUrl ? (
          <img src={movie.posterUrl} alt={movie.title} loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <div className="h-full w-full bg-gradient-to-br from-slate-700 to-slate-900" />
        )}
      </div>
      <div className="p-3">
        <p className="truncate text-sm font-semibold text-gray-100">{movie.title}</p>
        <p className="text-xs text-gray-400">
          {movie.year}
          {movie.ageRating ? ` · ${movie.ageRating}` : ''}
        </p>
        <span className="mt-2 block rounded-md bg-indigo-600 px-3 py-1.5 text-center text-xs font-semibold text-white group-hover:bg-indigo-500">
          예매하기
        </span>
      </div>
    </Link>
  )
}

export default NowShowingCard
