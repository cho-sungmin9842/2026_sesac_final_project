import { Link } from 'react-router-dom'
import MovieCard from '../../../components/common/MovieCard'

// lg 기준 한 줄에 7개씩이라, 14개(2줄)까지만 보여주고 나머지는 "전체보기"로 유도합니다.
const MAX_VISIBLE_MOVIES = 14

// maxVisible을 넘겨 기본 2줄 캡을 풀 수 있습니다(예: KMDB 응답을 자르지 않고 받은 그대로 보여줘야
// 하는 "지금 인기 있는 영화"). showViewAll은 호출하는 쪽이 명시적으로 정합니다 - "최근 조회한 영화"
// 처럼 홈 화면에는 일부러 14개만 받아와 보여주는 섹션은 movies.length가 항상 14 이하라서 "더 있는지"를
// movies 배열 길이만으로는 판단할 수 없기 때문입니다(실제로는 서버에 최대 100건까지 더 있을 수 있음).
function MovieSection({
  icon,
  title,
  movies,
  viewAllTo = '/movies',
  viewAllLabel = '전체보기',
  maxVisible = MAX_VISIBLE_MOVIES,
  showViewAll = true,
}) {
  const visibleMovies = movies.slice(0, maxVisible)
  return (
    <section className="mt-8">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-base font-bold text-gray-100">
          <span>{icon}</span>
          {title}
        </h3>
        {showViewAll && (
          <Link to={viewAllTo} className="text-sm text-gray-400 hover:text-white">
            {viewAllLabel}
          </Link>
        )}
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
