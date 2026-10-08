function ChatBubble({ from, isError, children }) {
  const isUser = from === 'user'

  const bubbleStyle = isUser
    ? 'bg-indigo-600 text-white'
    : isError
      ? 'border border-red-500/40 bg-red-500/10 text-red-200'
      : 'bg-slate-900 text-gray-200'

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div className={`max-w-xl whitespace-pre-wrap rounded-xl px-4 py-3 text-sm ${bubbleStyle}`}>{children}</div>
    </div>
  )
}

export default ChatBubble
