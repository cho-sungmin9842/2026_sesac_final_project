import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { clearChatHistory, getChatHistory, sendChatMessage } from '../../api/chatApi'
import ChatBubble from './components/ChatBubble'
import ChatMovieRecommendation from './components/ChatMovieRecommendation'

const QUICK_REPLIES = [
  '같은 장르의 영화들을 추천해줘',
  '이번 주말에 볼만한 영화 추천해줘',
  '짧고 가볍게 볼 영화 추천해줘',
  '이 영화 찜해줘',
]

function formatToday() {
  const date = new Date()
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${yyyy}.${mm}.${dd}`
}

const GREETING = { id: 0, from: 'ai', type: 'text', content: '안녕하세요! 저는 새싹무비 AI 큐레이터예요 🎬 오늘 어떤 영화를 찾아드릴까요?' }

// DB에 저장된 메시지(role/content/movies)를 화면에서 쓰는 메시지 형태로 바꿉니다.
function fromSavedMessage(saved, id) {
  const from = saved.role === 'user' ? 'user' : 'ai'
  if (from === 'ai' && saved.movies.length > 0) {
    return { id, from, type: 'recommendation', analysis: saved.content, movies: saved.movies }
  }
  return { id, from, type: 'text', content: saved.content }
}

// 스피너 대신 말풍선 안에서 점 3개가 번갈아 깜빡이는 타이핑 표시입니다.
function TypingIndicator() {
  return (
    <span className="flex items-center gap-1 py-0.5">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400"
          style={{ animationDelay: `${i * 0.15}s` }}
        />
      ))}
    </span>
  )
}

function AiChatPage() {
  const { user } = useAuth()
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [isSending, setIsSending] = useState(false)
  const nextId = useRef(1)
  const bottomRef = useRef(null)

  // 접속 시점의 실제 날짜를 그때그때 반영해야 하니, 고정 배열이 아니라 렌더링마다 새로 만듭니다.
  const quickReplies = [...QUICK_REPLIES, `오늘(${formatToday()} 기준) 상영중인 영화를 찾아줘`]

  useEffect(() => {
    let cancelled = false
    getChatHistory(user.id)
      .then((saved) => {
        if (cancelled) return
        // 저장된 대화가 있으면 그것만 보여주고(인사말을 또 앞에 붙이지 않음), 없을 때만(새 사용자) 인사말을 보여줍니다.
        if (saved.length === 0) {
          setMessages([GREETING])
          return
        }
        const loaded = saved.map((item) => fromSavedMessage(item, nextId.current++))
        setMessages(loaded)
      })
      .catch(() => {
        // 이전 대화를 못 불러와도 새 대화는 계속할 수 있어야 하니, 인사말만 보여주고 조용히 넘어갑니다.
        if (!cancelled) setMessages([GREETING])
      })
      .finally(() => {
        if (!cancelled) setIsLoadingHistory(false)
      })
    return () => {
      cancelled = true
    }
  }, [user.id])

  // 메시지가 추가될 때마다 맨 아래로 스크롤합니다(새 답변이 와도 사용자가 직접 내릴 필요 없게).
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = async (text) => {
    const trimmed = text.trim()
    if (!trimmed || isSending) return

    const userMessage = { id: nextId.current++, from: 'user', type: 'text', content: trimmed }
    const loadingMessageId = nextId.current++
    setMessages((prev) => [...prev, userMessage, { id: loadingMessageId, from: 'ai', type: 'loading' }])
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
            : {
                id: loadingMessageId,
                from: 'ai',
                type: 'text',
                isError: true,
                content: `죄송해요, 오류가 발생했어요: ${error.message}`,
              },
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

  const handleNewConversation = async () => {
    if (isSending) return
    if (!window.confirm('지금까지의 대화 내역을 전부 지우고 새로 시작할까요?')) return
    try {
      await clearChatHistory(user.id)
      nextId.current = 1
      setMessages([GREETING])
    } catch (error) {
      window.alert(error.message)
    }
  }

  return (
    <div className="mx-auto flex h-[calc(100svh-64px)] max-w-4xl flex-col px-6 py-6">
      <div className="mb-3 flex items-center justify-between">
        <h1 className="text-lg font-bold text-gray-100">AI 추천</h1>
        <button
          type="button"
          onClick={handleNewConversation}
          className="rounded-lg border border-gray-700 px-3 py-1.5 text-xs text-gray-300 hover:bg-slate-800"
        >
          새 대화 시작
        </button>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto pb-4">
        {isLoadingHistory && <p className="text-xs text-gray-500">이전 대화를 불러오는 중...</p>}
        {messages.map((message) =>
          message.type === 'recommendation' ? (
            <ChatBubble key={message.id} from={message.from}>
              <ChatMovieRecommendation analysis={message.analysis} movies={message.movies} />
            </ChatBubble>
          ) : message.type === 'loading' ? (
            <ChatBubble key={message.id} from={message.from}>
              <TypingIndicator />
            </ChatBubble>
          ) : (
            <ChatBubble key={message.id} from={message.from} isError={message.isError}>
              {message.content}
            </ChatBubble>
          ),
        )}
        <div ref={bottomRef} />
      </div>

      <div className="mb-3 flex flex-wrap gap-2">
        {quickReplies.map((reply) => (
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
          className="flex w-11 items-center justify-center rounded-lg bg-indigo-600 text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isSending ? (
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />
          ) : (
            '➤'
          )}
        </button>
      </form>
    </div>
  )
}

export default AiChatPage
