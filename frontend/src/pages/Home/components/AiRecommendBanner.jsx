import { Link } from 'react-router-dom'

function AiRecommendBanner() {
  return (
    <section className="flex items-center justify-between rounded-xl bg-gradient-to-r from-indigo-600 via-purple-600 to-fuchsia-600 px-8 py-6">
      <div>
        <h2 className="flex items-center gap-2 text-xl font-bold text-white">
          🎬 AI에게 오늘의 영화 추천받기
        </h2>
        <p className="mt-1 text-sm text-indigo-100">
          기분, 시간, 취향만 말해주면 AI가 딱 맞는 영화를 골라드려요
        </p>
      </div>
      <Link
        to="/ai-chat"
        className="whitespace-nowrap rounded-lg bg-white px-4 py-2 text-sm font-semibold text-indigo-700 hover:bg-indigo-50"
      >
        AI 챗봇 시작하기 →
      </Link>
    </section>
  )
}

export default AiRecommendBanner
