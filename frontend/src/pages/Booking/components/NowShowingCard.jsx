import { Link } from 'react-router-dom'
import PosterPlaceholder from '../../../components/common/PosterPlaceholder'

// 포스터를 누르면 영화 상세보기로, "예매하기" 버튼을 누르면 좌석 선택 화면으로 - 목적지가 서로 달라서
// 카드 전체를 하나의 링크로 감싸지 않고 두 개의 링크로 나눴습니다.
function NowShowingCard({ movie }) {
  return (
    <div className="group overflow-hidden rounded-lg bg-slate-900 transition-transform hover:-translate-y-1">
      <Link to={`/movies/${movie.id}`} className="block">
        <div className="relative aspect-[3/4] w-full overflow-hidden bg-slate-800">
          {movie.posterUrl ? (
            <img src={movie.posterUrl} alt={movie.title} loading="lazy" className="h-full w-full object-cover" />
          ) : (
            <PosterPlaceholder />
          )}
          <span className="absolute top-2 left-2 max-w-[65%] rounded-md bg-black/60 px-2 py-1 text-[11px] font-semibold leading-tight text-gray-200">
            {movie.ageRating ?? '정보 없음'}
          </span>
          <span
            className={`absolute top-2 right-2 flex items-center gap-1 rounded-md bg-black/60 px-2 py-1 text-xs font-semibold ${
              movie.averageScore != null ? 'text-amber-400' : 'text-gray-400'
            }`}
          >
            {movie.averageScore != null ? `★ ${movie.averageScore.toFixed(1)}` : '정보 없음'}
          </span>
        </div>
      </Link>
      <div className="p-3">
        <Link
          to={`/movies/${movie.id}`}
          className="block truncate text-sm font-semibold text-gray-100 hover:underline"
        >
          {movie.title}
        </Link>
        <p className="text-xs text-gray-400">
          {movie.year}
          {movie.ageRating ? ` · ${movie.ageRating}` : ''}
        </p>
        <Link
          to={`/booking/${movie.id}`}
          className="mt-2 block rounded-md bg-indigo-600 px-3 py-1.5 text-center text-xs font-semibold text-white group-hover:bg-indigo-500"
        >
          예매하기
        </Link>
      </div>
    </div>
  )
}

export default NowShowingCard
