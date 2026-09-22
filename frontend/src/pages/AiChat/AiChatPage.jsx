import { useEffect, useState } from 'react'
import { getFirstMatches } from '../../api/movieApi'
import ChatBubble from './components/ChatBubble'
import ChatMovieRecommendation from './components/ChatMovieRecommendation'

const QUICK_REPLIES = ['비슷한 영화 더 추천해줘', '이 영화 비하인드 스토리 알려줘']

// 실제 AI 추천 엔진이 붙기 전까지, 데모 대화에서 추천할 영화를 KMDB에서 실시간으로 찾아옵니다.
const RECOMMENDED_TITLES = ['그놈이다', '밀수', '핸섬가이즈']

const INTRO_MESSAGES = [
  { id: 1, from: 'ai', type: 'text', content: '안녕하세요! 저는 무비픽 AI 큐레이터예요 🎬 오늘 어떤 영화를 찾아드릴까요?' },
  { id: 2, from: 'user', type: 'text', content: '이번 주말에 볼 스릴러 영화 추천해줘. 2시간 안 넘었으면 좋겠어' },
]

function AiChatPage() {
  const [messages, setMessages] = useState(INTRO_MESSAGES)
  const [input, setInput] = useState('')

  useEffect(() => {
    let cancelled = false

    getFirstMatches(RECOMMENDED_TITLES).then((movies) => {
      if (cancelled || movies.length === 0) return
      setMessages((prev) => [
        ...prev,
        {
          id: prev.length + 1,
          from: 'ai',
          type: 'recommendation',
          analysis: "최근 찜하신 '올드보이', '추격자' 평점을 참고해서 러닝타임 2시간 이하의 스릴러를 골라봤어요.",
          movies,
        },
        { id: prev.length + 2, from: 'user', type: 'text', content: '좋아, 이 3개 찜 목록에 담아줘' },
        {
          id: prev.length + 3,
          from: 'ai',
          type: 'text',
          content: `✅ ${movies.length}편 모두 찜 목록에 담았어요! 주말에 재미있게 감상하세요 🍿`,
        },
      ])
    })

    return () => {
      cancelled = true
    }
  }, [])

  const sendMessage = (text) => {
    const trimmed = text.trim()
    if (!trimmed) return

    setMessages((prev) => [
      ...prev,
      { id: prev.length + 1, from: 'user', type: 'text', content: trimmed },
      {
        id: prev.length + 2,
        from: 'ai',
        type: 'text',
        content: '조금만 기다려주세요, 취향을 분석해서 딱 맞는 영화를 찾아드릴게요!',
      },
    ])
    setInput('')
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    sendMessage(input)
  }

  return (
    <div className="mx-auto flex h-[calc(100svh-64px)] max-w-4xl flex-col px-6 py-6">
      <div className="flex-1 space-y-4 overflow-y-auto pb-4">
        {messages.map((message) =>
          message.type === 'recommendation' ? (
            <ChatBubble key={message.id} from={message.from}>
              <ChatMovieRecommendation analysis={message.analysis} movies={message.movies} />
            </ChatBubble>
          ) : (
            <ChatBubble key={message.id} from={message.from}>
              {message.content}
            </ChatBubble>
          ),
        )}
      </div>

      <div className="mb-3 flex gap-2">
        {QUICK_REPLIES.map((reply) => (
          <button
            key={reply}
            type="button"
            onClick={() => sendMessage(reply)}
            className="rounded-full border border-gray-700 px-3 py-1.5 text-xs text-gray-300 hover:bg-slate-800"
          >
            {reply}
          </button>
        ))}
      </div>

      <form onSubmit={handleSubmit} className="flex gap-2">
        <input
          value={input}
          onChange={(event) => setInput(event.target.value)}
          type="text"
          placeholder="메시지를 입력하세요..."
          className="flex-1 rounded-lg bg-slate-900 px-4 py-3 text-sm text-gray-100 placeholder:text-gray-500 focus:outline-none"
        />
        <button
          type="submit"
          className="flex items-center justify-center rounded-lg bg-indigo-600 px-4 text-white hover:bg-indigo-500"
        >
          ➤
        </button>
      </form>
    </div>
  )
}

export default AiChatPage
