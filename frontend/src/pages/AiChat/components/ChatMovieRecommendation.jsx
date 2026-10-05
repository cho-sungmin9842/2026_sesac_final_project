import MovieCard from '../../../components/common/MovieCard'

// "취향 분석 완료!"를 모든 답변 앞에 고정으로 붙였었는데, "지금 상영중인 영화" 같은 답변에는 어울리지
// 않아서(취향을 분석한 게 아니니까) 제거했습니다. 서버가 보내는 문구 자체가 이미 그 맥락에 맞게 조립돼
// 있어서(describeFilter 등) 따로 안내문을 덧붙이지 않아도 됩니다.
// 카드 개수가 3개보다 적을 때 grid-cols-3가 빈 칸처럼 휑해 보이는 걸 막기 위해, grid 대신 flex-wrap +
// 카드별 고정 너비를 씁니다(MovieCard가 w-full이라 부모 너비만큼 늘어나므로, 부모를 고정폭으로 감쌉니다).
function ChatMovieRecommendation({ analysis, movies }) {
  return (
    <div className="max-w-xl rounded-xl bg-slate-900 p-4">
      <p className="whitespace-pre-line text-sm text-gray-200">{analysis}</p>
      <div className="mt-3 flex flex-wrap gap-3">
        {movies.map((movie) => (
          <div key={movie.id} className="w-28 sm:w-32">
            <MovieCard movie={movie} />
          </div>
        ))}
      </div>
    </div>
  )
}

export default ChatMovieRecommendation
