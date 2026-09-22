import { Link } from 'react-router-dom'
import MovieCard from '../../../components/common/MovieCard'

function MovieSection({ icon, title, movies, viewAllTo = '/movies' }) {
  return (
    <section className="mt-8">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-base font-bold text-gray-100">
          <span>{icon}</span>
          {title}
        </h3>
        <Link to={viewAllTo} className="text-sm text-gray-400 hover:text-white">
          전체보기
        </Link>
      </div>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-7">
        {movies.map((movie) => (
          <MovieCard key={movie.id} movie={movie} />
        ))}
      </div>
    </section>
  )
}

export default MovieSection
