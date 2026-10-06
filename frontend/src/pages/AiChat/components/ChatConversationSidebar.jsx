import { useEffect, useState } from 'react'

// ChatGPT 사이드바처럼 왼쪽에 과거 대화방 목록을 보여줍니다. 제목은 그 대화의 첫 메시지를 간단히
// 줄인 값(백엔드 ChatService.deriveTitle)이라, 어떤 대화였는지 한눈에 구분할 수 있습니다.
function ChatConversationSidebar({ conversations, activeConversationId, onSelect, onDelete, isLoading }) {
  // 우클릭한 대화방의 위치/id를 기억해뒀다가 그 좌표에 "삭제" 메뉴를 띄웁니다.
  const [contextMenu, setContextMenu] = useState(null) // { x, y, conversationId } | null

  // 메뉴가 떠 있는 동안 아무 데나 왼쪽 클릭하거나 Esc를 누르면 닫습니다.
  // (주의: window의 'contextmenu'는 여기서 감시하면 안 됩니다 - 다른 대화를 다시 우클릭할 때 그
  // 우클릭 이벤트 하나가 "새 메뉴 열기"와 "메뉴 닫기"를 동시에 발생시켜서, 같은 렌더링에 두 상태
  // 업데이트가 함께 배치 처리되며 메뉴가 열리자마자 바로 닫혀버리는(=화면에 전혀 안 보이는) 문제가
  // 있었습니다.)
  useEffect(() => {
    if (!contextMenu) return undefined
    const closeMenu = () => setContextMenu(null)
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') closeMenu()
    }
    window.addEventListener('click', closeMenu)
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('click', closeMenu)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [contextMenu])

  const handleContextMenu = (event, conversationId) => {
    event.preventDefault()
    setContextMenu({ x: event.clientX, y: event.clientY, conversationId })
  }

  const handleDeleteClick = () => {
    if (contextMenu) onDelete(contextMenu.conversationId)
    setContextMenu(null)
  }

  return (
    <aside className="hidden w-56 shrink-0 flex-col border-r border-white/10 pr-3 sm:flex">
      <h2 className="mb-2 px-2 text-xs font-semibold uppercase tracking-wide text-gray-500">대화 기록</h2>
      <div className="flex-1 space-y-1 overflow-y-auto">
        {isLoading && <p className="px-2 text-xs text-gray-500">불러오는 중...</p>}
        {!isLoading && conversations.length === 0 && (
          <p className="px-2 text-xs text-gray-500">아직 지난 대화가 없어요.</p>
        )}
        {conversations.map((conversation) => (
          <button
            key={conversation.id}
            type="button"
            onClick={() => onSelect(conversation.id)}
            onContextMenu={(event) => handleContextMenu(event, conversation.id)}
            title={conversation.title ?? '새 대화'}
            className={`block w-full truncate rounded-lg px-2 py-2 text-left text-sm transition ${
              conversation.id === activeConversationId
                ? 'bg-indigo-600/20 text-indigo-300'
                : 'text-gray-300 hover:bg-slate-800'
            }`}
          >
            {conversation.title ?? '새 대화'}
          </button>
        ))}
      </div>

      {contextMenu && (
        <div
          className="fixed z-50 min-w-[100px] rounded-lg border border-white/10 bg-slate-800 py-1 shadow-lg"
          style={{ top: contextMenu.y, left: contextMenu.x }}
        >
          <button
            type="button"
            onClick={handleDeleteClick}
            className="block w-full px-3 py-1.5 text-left text-sm text-red-400 hover:bg-slate-700"
          >
            삭제
          </button>
        </div>
      )}
    </aside>
  )
}

export default ChatConversationSidebar
