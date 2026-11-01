import { Link } from 'react-router-dom'
import MovieCard from '../../../components/common/MovieCard'

// lg 기준 한 줄에 7개씩이라, 14개(2줄)까지만 보여주고 나머지는 "전체보기"로 유도합니다.
const MAX_VISIBLE_MOVIES = 14

function MovieSection({ icon, title, movies, viewAllTo = '/movies', viewAllLabel = '전체보기' }) {
  const visibleMovies = movies.slice(0, MAX_VISIBLE_MOVIES)
  return (
    <section className="mt-8">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-base font-bold text-gray-100">
          <span>{icon}</span>
          {title}
        </h3>
        <Link to={viewAllTo} className="text-sm text-gray-400 hover:text-white">
          {viewAllLabel}
        </Link>
      </div>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-7">
        {visibleMovies.map((movie) => (
          <MovieCard key={movie.id} movie={movie} />
        ))}
      </div>
    </section>
  )
}

export default MovieSection
