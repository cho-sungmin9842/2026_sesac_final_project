function ChatBubble({ from, children }) {
  const isUser = from === 'user'

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-xl rounded-xl px-4 py-3 text-sm ${
          isUser ? 'bg-indigo-600 text-white' : 'bg-slate-900 text-gray-200'
        }`}
      >
        {children}
      </div>
    </div>
  )
}

export default ChatBubble
