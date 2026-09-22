import MovieCard from '../../../components/common/MovieCard'

function ChatMovieRecommendation({ analysis, movies }) {
  return (
    <div className="max-w-xl rounded-xl bg-slate-900 p-4">
      <p className="text-sm text-gray-200">
        <span className="font-bold text-indigo-300">취향 분석 완료!</span> {analysis}
      </p>
      <div className="mt-3 grid grid-cols-3 gap-3">
        {movies.map((movie) => (
          <MovieCard key={movie.id} movie={movie} />
        ))}
      </div>
    </div>
  )
}

export default ChatMovieRecommendation
