import { Link } from 'react-router-dom'

function AiRecommendBanner() {
  return (
    <section className="flex flex-col gap-4 rounded-xl bg-gradient-to-r from-indigo-600 via-purple-600 to-fuchsia-600 px-6 py-6 sm:flex-row sm:items-center sm:justify-between sm:px-8">
      <div>
        <h2 className="flex items-center gap-2 text-xl font-bold text-white">
          🎬 AI에게 오늘의 영화 추천받기
        </h2>
      </div>
      <Link
        to="/ai-chat"
        className="self-start whitespace-nowrap rounded-lg bg-white px-4 py-2 text-sm font-semibold text-indigo-700 hover:bg-indigo-50 sm:self-auto"
      >
        AI 챗봇 시작하기 →
      </Link>
    </section>
  )
}

export default AiRecommendBanner
