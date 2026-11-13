import { useEffect, useState } from 'react'

// ChatGPT 사이드바처럼 왼쪽에 과거 대화방 목록을 보여줍니다. 제목은 그 대화의 첫 메시지를 간단히
// 줄인 값(백엔드 ChatService.deriveTitle)이라, 어떤 대화였는지 한눈에 구분할 수 있습니다.
// sm(640px) 이상에서는 항상 보이는 고정 사이드바이고, 그 미만(모바일)에서는 AdminSidebar와 같은
// 패턴으로 open일 때만 펼쳐지는 오버레이로 동작합니다 - 전에는 모바일에서 아예 숨겨져서 과거
// 대화 전환/삭제가 불가능했습니다.
function ChatConversationSidebar({ conversations, activeConversationId, onSelect, onDelete, isLoading, open, onClose }) {
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

  const handleSelect = (conversationId) => {
    onSelect(conversationId)
    onClose?.()
  }

  return (
    <>
      {open && <div className="fixed inset-0 z-40 bg-black/50 sm:hidden" onClick={onClose} aria-hidden="true" />}
      <aside
        className={`fixed inset-y-0 left-0 z-50 flex w-64 shrink-0 flex-col bg-slate-950 px-3 py-4 transition-transform duration-200 sm:static sm:z-auto sm:w-56 sm:translate-x-0 sm:border-r sm:border-white/10 sm:bg-transparent sm:px-0 sm:py-0 sm:pr-3 ${
          open ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="mb-2 flex items-center justify-between px-2 sm:px-0">
          <h2 className="text-[16px] font-semibold uppercase tracking-wide text-white">대화 기록</h2>
          <button type="button" onClick={onClose} className="text-gray-400 hover:text-white sm:hidden" aria-label="닫기">
            ✕
          </button>
        </div>
        <div className="flex-1 space-y-1 overflow-y-auto">
          {isLoading && <p className="px-2 text-xs text-gray-500">불러오는 중...</p>}
          {!isLoading && conversations.length === 0 && (
            <p className="px-2 text-xs text-gray-500">아직 지난 대화가 없어요.</p>
          )}
          {conversations.map((conversation) => (
            <button
              key={conversation.id}
              type="button"
              onClick={() => handleSelect(conversation.id)}
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
    </>
  )
}

export default ChatConversationSidebar
