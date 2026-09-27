import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { getChatHistory, sendChatMessage } from '../../api/chatApi'
import ChatBubble from './components/ChatBubble'
import ChatMovieRecommendation from './components/ChatMovieRecommendation'

const QUICK_REPLIES = ['비슷한 영화 더 추천해줘', '이 영화 비하인드 스토리 알려줘']

const GREETING = { id: 0, from: 'ai', type: 'text', content: '안녕하세요! 저는 새싹무비 AI 큐레이터예요 🎬 오늘 어떤 영화를 찾아드릴까요?' }

// DB에 저장된 메시지(role/content/movies)를 화면에서 쓰는 메시지 형태로 바꿉니다.
function fromSavedMessage(saved, id) {
  const from = saved.role === 'user' ? 'user' : 'ai'
  if (from === 'ai' && saved.movies.length > 0) {
    return { id, from, type: 'recommendation', analysis: saved.content, movies: saved.movies }
  }
  return { id, from, type: 'text', content: saved.content }
}

function AiChatPage() {
  const { user } = useAuth()
  const [messages, setMessages] = useState([GREETING])
  const [input, setInput] = useState('')
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [isSending, setIsSending] = useState(false)
  const nextId = useRef(1)

  useEffect(() => {
    let cancelled = false
    getChatHistory(user.id)
      .then((saved) => {
        if (cancelled || saved.length === 0) return
        const loaded = saved.map((item) => fromSavedMessage(item, nextId.current++))
        setMessages((prev) => [...prev, ...loaded])
      })
      .catch(() => {
        // 이전 대화를 못 불러와도 새 대화는 계속할 수 있어야 하니 조용히 넘어갑니다.
      })
      .finally(() => {
        if (!cancelled) setIsLoadingHistory(false)
      })
    return () => {
      cancelled = true
    }
  }, [user.id])

  const sendMessage = async (text) => {
    const trimmed = text.trim()
    if (!trimmed || isSending) return

    const userMessage = { id: nextId.current++, from: 'user', type: 'text', content: trimmed }
    const loadingMessageId = nextId.current++
    setMessages((prev) => [
      ...prev,
      userMessage,
      { id: loadingMessageId, from: 'ai', type: 'text', content: '취향을 분석해서 딱 맞는 영화를 찾고 있어요...' },
    ])
    setInput('')
    setIsSending(true)

    try {
      const { reply, movies } = await sendChatMessage(user.id, trimmed)
      setMessages((prev) =>
        prev.map((message) =>
          message.id !== loadingMessageId
            ? message
            : movies.length > 0
              ? { id: loadingMessageId, from: 'ai', type: 'recommendation', analysis: reply, movies }
              : { id: loadingMessageId, from: 'ai', type: 'text', content: reply },
        ),
      )
    } catch (error) {
      setMessages((prev) =>
        prev.map((message) =>
          message.id !== loadingMessageId
            ? message
            : { id: loadingMessageId, from: 'ai', type: 'text', content: `죄송해요, 오류가 발생했어요: ${error.message}` },
        ),
      )
    } finally {
      setIsSending(false)
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    sendMessage(input)
  }

  return (
    <div className="mx-auto flex h-[calc(100svh-64px)] max-w-4xl flex-col px-6 py-6">
      <div className="flex-1 space-y-4 overflow-y-auto pb-4">
        {isLoadingHistory && <p className="text-xs text-gray-500">이전 대화를 불러오는 중...</p>}
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
            disabled={isSending}
            onClick={() => sendMessage(reply)}
            className="rounded-full border border-gray-700 px-3 py-1.5 text-xs text-gray-300 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
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
          disabled={isSending}
          placeholder="메시지를 입력하세요..."
          className="flex-1 rounded-lg bg-slate-900 px-4 py-3 text-sm text-gray-100 placeholder:text-gray-500 focus:outline-none disabled:opacity-60"
        />
        <button
          type="submit"
          disabled={isSending}
          className="flex items-center justify-center rounded-lg bg-indigo-600 px-4 text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          ➤
        </button>
      </form>
    </div>
  )
}

export default AiChatPage
