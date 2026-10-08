import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { useWishlist } from '../../wishlist/WishlistContext'
import { deleteConversation, getConversationMessages, getConversations, sendChatMessage } from '../../api/chatApi'
import ChatBubble from './components/ChatBubble'
import ChatMovieRecommendation from './components/ChatMovieRecommendation'
import ChatConversationSidebar from './components/ChatConversationSidebar'
import ChatSeatStatus from './components/ChatSeatStatus'

const QUICK_REPLIES = [
  '같은 장르의 영화들을 추천해줘',
  '이번 주말에 볼만한 영화 추천해줘',
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

// DB에 저장된 메시지(role/content/movies/seatStatus)를 화면에서 쓰는 메시지 형태로 바꿉니다.
function fromSavedMessage(saved, id) {
  const from = saved.role === 'user' ? 'user' : 'ai'
  if (from === 'ai' && saved.seatStatus) {
    return { id, from, type: 'seatStatus', analysis: saved.content, seatStatus: saved.seatStatus }
  }
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
  const { refresh: refreshWishlistedIds } = useWishlist()
  const [conversations, setConversations] = useState([])
  const [isLoadingConversations, setIsLoadingConversations] = useState(true)
  // null이면 아직 메시지를 한 번도 보내지 않은 "새 대화" 상태입니다(서버에 대화방이 없고, 첫 메시지를
  // 보내야 비로소 생깁니다).
  const [activeConversationId, setActiveConversationId] = useState(null)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [isSending, setIsSending] = useState(false)
  const nextId = useRef(1)
  const bottomRef = useRef(null)
  const inputRef = useRef(null)
  // sendMessage가 새로 만들어진 conversationId를 activeConversationId에 반영할 때, 그 대화의 메시지는
  // 이미 화면에 다 있으므로 바로 아래 effect가 서버에서 또 불러오지 않도록 막는 플래그입니다.
  const skipNextFetchRef = useRef(false)

  // 접속 시점의 실제 날짜를 그때그때 반영해야 하니, 고정 배열이 아니라 렌더링마다 새로 만듭니다.
  // 화면에는 짧게 "오늘 상영중인 영화를 찾아줘"라고 보여주되, 실제로 서버에 보내는 메시지는 그대로
  // "오늘(YYYY.MM.DD 기준) 상영중인 영화를 찾아줘"입니다 - 버튼 문구만 간결하게 바꾸고 동작(채팅 내용,
  // 백엔드 처리)은 기존과 똑같이 유지하기 위해 화면 표시(label)와 실제 전송 내용(message)을 분리했습니다.
  const quickReplies = [
    { label: '오늘 상영중인 영화를 찾아줘', message: `오늘(${formatToday()} 기준) 상영중인 영화를 찾아줘` },
    ...QUICK_REPLIES.map((text) => ({ label: text, message: text })),
  ]

  // 대화방 목록을 불러와서, 지난 대화가 있으면 가장 최근 대화를 이어서 보여주고(원래 하던 대로 "이어서
  // 계속하기"), 하나도 없는 완전히 새 사용자일 때만 인사말을 보여줍니다.
  useEffect(() => {
    let cancelled = false
    getConversations(user.id)
      .then((list) => {
        if (cancelled) return
        setConversations(list)
        if (list.length > 0) {
          setActiveConversationId(list[0].id)
        } else {
          setMessages([GREETING])
          setIsLoadingHistory(false)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setMessages([GREETING])
          setIsLoadingHistory(false)
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoadingConversations(false)
      })
    return () => {
      cancelled = true
    }
  }, [user.id])

  // activeConversationId가 바뀔 때마다(사이드바 클릭, 또는 첫 메시지로 새 대화방이 막 생겼을 때) 그
  // 대화방의 메시지를 불러옵니다.
  useEffect(() => {
    if (activeConversationId == null) return undefined
    if (skipNextFetchRef.current) {
      skipNextFetchRef.current = false
      return undefined
    }

    let cancelled = false
    setIsLoadingHistory(true)
    getConversationMessages(user.id, activeConversationId)
      .then((saved) => {
        if (cancelled) return
        // 사이드바에서 지난 대화를 열었을 때도 "새 대화 시작"과 똑같이 맨 위에 인사말이 보이도록,
        // 저장된 메시지 앞에 항상 인사말을 붙입니다(인사말 자체는 DB에 저장되지 않는 화면 전용 메시지).
        const loaded = [GREETING, ...saved.map((item) => fromSavedMessage(item, nextId.current++))]
        setMessages(loaded)
      })
      .catch(() => {
        if (!cancelled) setMessages([GREETING])
      })
      .finally(() => {
        if (!cancelled) setIsLoadingHistory(false)
      })
    return () => {
      cancelled = true
    }
  }, [user.id, activeConversationId])

  // 메시지가 추가될 때마다 맨 아래로 스크롤합니다(새 답변이 와도 사용자가 직접 내릴 필요 없게).
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Shift+Enter로 줄바꿈한 내용이 입력창 안에서 가려지지 않도록, 입력 내용에 맞춰 높이를 늘립니다
  // (일정 높이 이상이 되면 CSS max-height로 막아두고 그 다음부터는 스크롤됩니다).
  useEffect(() => {
    const textarea = inputRef.current
    if (!textarea) return
    textarea.style.height = 'auto'
    textarea.style.height = `${textarea.scrollHeight}px`
  }, [input])

  const sendMessage = async (text) => {
    const trimmed = text.trim()
    if (!trimmed || isSending) return

    const userMessage = { id: nextId.current++, from: 'user', type: 'text', content: trimmed }
    const loadingMessageId = nextId.current++
    setMessages((prev) => [...prev, userMessage, { id: loadingMessageId, from: 'ai', type: 'loading' }])
    setInput('')
    setIsSending(true)

    try {
      const { conversationId, reply, movies, seatStatus } = await sendChatMessage(user.id, activeConversationId, trimmed)
      if (conversationId !== activeConversationId) {
        // 새 대화의 첫 메시지라 서버가 방금 대화방을 만든 경우입니다 - 메시지는 이미 화면에 있으니
        // 아래 "대화방 전환" effect가 다시 불러오지 않도록 막아둡니다.
        skipNextFetchRef.current = true
        setActiveConversationId(conversationId)
      }
      setMessages((prev) =>
        prev.map((message) =>
          message.id !== loadingMessageId
            ? message
            : seatStatus
              ? { id: loadingMessageId, from: 'ai', type: 'seatStatus', analysis: reply, seatStatus }
              : movies.length > 0
                ? { id: loadingMessageId, from: 'ai', type: 'recommendation', analysis: reply, movies }
                : { id: loadingMessageId, from: 'ai', type: 'text', content: reply },
        ),
      )
      // 사이드바 제목/순서(최근 대화가 위로)를 최신 상태로 맞춥니다.
      getConversations(user.id)
        .then(setConversations)
        .catch(() => {})
      // "이 영화 찜해줘"/"찜 해제해줘"처럼 채팅으로 찜 목록이 바뀌었을 수 있는데, WishlistContext는
      // 로그인 시 한 번만 불러온 뒤 toggle()을 거치지 않은 변경은 알 방법이 없어서 북마크 표시가 낡은
      // 상태로 남아있었습니다(예: 찜 목록 조회 응답의 카드들이 전부 찜 안 된 것처럼 보임). 매 응답마다
      // 다시 불러와 맞춥니다.
      refreshWishlistedIds().catch(() => {})
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

  // "새 대화 시작" - 지난 대화는 그대로 두고(사이드바에서 언제든 다시 볼 수 있음), 화면만 빈 새 대화로
  // 바꿉니다. 서버에는 첫 메시지를 보낼 때 비로소 대화방이 생기므로, 여기서는 별도 API 호출이 없습니다.
  const handleNewConversation = () => {
    if (isSending) return
    setActiveConversationId(null)
    setMessages([GREETING])
    nextId.current = 1
  }

  const handleSelectConversation = (conversationId) => {
    if (isSending || conversationId === activeConversationId) return
    setActiveConversationId(conversationId)
  }

  // 사이드바 우클릭 메뉴의 "삭제" - 지금 보고 있던 대화방을 지웠으면 화면을 빈 새 대화로 되돌립니다.
  const handleDeleteConversation = async (conversationId) => {
    if (!window.confirm('이 대화를 삭제할까요?')) return
    try {
      await deleteConversation(user.id, conversationId)
      setConversations((prev) => prev.filter((conversation) => conversation.id !== conversationId))
      if (conversationId === activeConversationId) {
        setActiveConversationId(null)
        setMessages([GREETING])
      }
    } catch (error) {
      window.alert(error.message)
    }
  }

  return (
    <div className="mx-auto flex h-[calc(100svh-64px)] max-w-6xl gap-4 px-6 py-6">
      <ChatConversationSidebar
        conversations={conversations}
        activeConversationId={activeConversationId}
        onSelect={handleSelectConversation}
        onDelete={handleDeleteConversation}
        isLoading={isLoadingConversations}
      />

      <div className="flex min-w-0 flex-1 flex-col">
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
            ) : message.type === 'seatStatus' ? (
              <ChatBubble key={message.id} from={message.from}>
                <ChatSeatStatus seatStatus={message.seatStatus} />
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
          {quickReplies.map(({ label, message }) => (
            <button
              key={message}
              type="button"
              disabled={isSending}
              onClick={() => sendMessage(message)}
              className="rounded-full border border-gray-700 px-3 py-1.5 text-xs text-gray-300 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {label}
            </button>
          ))}
        </div>

        <form onSubmit={handleSubmit} className="flex gap-2">
          <textarea
            ref={inputRef}
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={(event) => {
              // Enter만 누르면 전송하고, Shift+Enter는 줄바꿈만 하고 전송하지 않습니다(이어서 입력 가능).
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                sendMessage(input)
              }
            }}
            rows={1}
            disabled={isSending}
            placeholder="메시지를 입력하세요... (Shift+Enter로 줄바꿈)"
            className="max-h-40 flex-1 resize-none overflow-y-auto rounded-lg bg-slate-900 px-4 py-3 text-sm text-gray-100 placeholder:text-gray-500 focus:outline-none disabled:opacity-60"
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
    </div>
  )
}

export default AiChatPage
