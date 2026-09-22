import { useState } from 'react'
import AiTasteReport from './components/AiTasteReport'
import ProfileHeader from './components/ProfileHeader'

const USER = {
  nickname: '영화조아',
  joinedAt: '2025.03.12',
  tier: '시네필',
  reviewCount: 128,
  wishlistCount: 34,
  watchedCount: 19,
}

const TABS = ['찜한 영화', '시청완료', '내가 쓴 리뷰', 'AI 채팅 기록']

function MyPage() {
  const [activeTab, setActiveTab] = useState(TABS[0])

  return (
    <div className="mx-auto max-w-6xl px-6 py-6">
      <ProfileHeader user={USER} />

      <AiTasteReport text="최근 감상 패턴 분석: 스릴러(42%), 드라마(31%)를 선호하며 봉준호·박찬욱 감독 작품 평점이 높아요." />

      <div className="mt-8 flex gap-6 border-b border-white/5 text-sm font-medium text-gray-400">
        {TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            className={`-mb-px border-b-2 pb-3 ${
              activeTab === tab
                ? 'border-indigo-500 text-white'
                : 'border-transparent hover:text-gray-200'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* 찜/시청완료/리뷰/채팅기록은 로그인·저장 기능이 아직 없어 빈 상태만 보여줍니다. */}
      <div className="mt-6">
        <p className="text-sm text-gray-500">{activeTab} 목록이 아직 없습니다.</p>
      </div>
    </div>
  )
}

export default MyPage
