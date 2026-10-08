import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { addWishlist, getWishlist, removeWishlist } from '../api/wishlistApi'

// 어느 화면의 영화 포스터 카드든 찜 여부를 한 번만 불러와 공유하기 위한 전역 상태입니다.
// (카드마다 개별적으로 isWishlisted를 호출하면 목록 화면에서 N+1 호출이 발생합니다.)
const WishlistContext = createContext(null)

// 찜/찜 해제 성공 시 잠깐 떴다 사라지는 안내 팝업 문구가 보이는 시간(ms).
const TOAST_DURATION_MS = 1800

export function WishlistProvider({ children }) {
  const { user } = useAuth()
  const [wishlistedIds, setWishlistedIds] = useState(new Set())
  // AI 추천 탭이든 영화 목록이든, 북마크 아이콘을 눌러 찜/찜 해제가 실제로 반영됐을 때 화면 하단에
  // 잠깐 띄우는 안내 팝업입니다. Provider 하나에서 렌더링해서 MovieCard/영화 상세 등 toggle()을 쓰는
  // 모든 화면에 공통으로 적용됩니다.
  const [toastMessage, setToastMessage] = useState(null)
  const toastTimerRef = useRef(null)

  const showToast = useCallback((message) => {
    setToastMessage(message)
    clearTimeout(toastTimerRef.current)
    toastTimerRef.current = setTimeout(() => setToastMessage(null), TOAST_DURATION_MS)
  }, [])

  useEffect(() => () => clearTimeout(toastTimerRef.current), [])

  // AI 채팅("이 영화 찜해줘")처럼 toggle()을 거치지 않고 서버에 직접 찜이 추가/삭제되는 경로가 있어서,
  // 로그인 시 한 번만 불러온 뒤로는 그런 변경을 알 방법이 없었습니다(마이페이지에 가도 그때 서버에 실제로
  // 저장된 찜 목록과 이 화면이 들고 있던 목록이 어긋나, 방금 채팅으로 찜한 영화가 안 보이는 버그가
  // 있었습니다). 그래서 다시 불러올 수 있는 refresh()를 따로 빼뒀습니다.
  const refresh = useCallback(() => {
    if (!user) {
      setWishlistedIds(new Set())
      return Promise.resolve()
    }
    return getWishlist(user.id).then((list) => {
      setWishlistedIds(new Set(list.map((item) => item.movieId)))
    })
  }, [user])

  useEffect(() => {
    let cancelled = false
    if (!user) {
      setWishlistedIds(new Set())
      return undefined
    }
    getWishlist(user.id).then((list) => {
      if (cancelled) return
      setWishlistedIds(new Set(list.map((item) => item.movieId)))
    })
    return () => {
      cancelled = true
    }
  }, [user])

  const toggle = useCallback(
    async (movie) => {
      const { id, title, posterUrl } = movie
      const wasWishlisted = wishlistedIds.has(id)

      setWishlistedIds((prev) => {
        const next = new Set(prev)
        if (wasWishlisted) next.delete(id)
        else next.add(id)
        return next
      })

      try {
        if (wasWishlisted) {
          await removeWishlist(user.id, id)
        } else {
          await addWishlist(user.id, { movieId: id, movieTitle: title, posterUrl })
        }
        showToast(wasWishlisted ? '찜해제했습니다' : '찜했습니다')
      } catch (error) {
        // 요청이 실패하면 화면에 반영했던 낙관적 업데이트를 되돌립니다(실패 시에는 안내 팝업을 띄우지 않고
        // 기존대로 alert로 에러를 알립니다).
        setWishlistedIds((prev) => {
          const next = new Set(prev)
          if (wasWishlisted) next.add(id)
          else next.delete(id)
          return next
        })
        window.alert(error.message)
      }
    },
    [user, wishlistedIds, showToast],
  )

  return (
    <WishlistContext.Provider value={{ wishlistedIds, toggle, refresh }}>
      {children}
      {toastMessage && (
        <div className="pointer-events-none fixed inset-x-0 bottom-8 z-[100] flex justify-center px-4">
          <div className="rounded-full bg-slate-900/95 px-5 py-2.5 text-sm font-semibold text-white shadow-lg ring-1 ring-white/10">
            {toastMessage}
          </div>
        </div>
      )}
    </WishlistContext.Provider>
  )
}

export function useWishlist() {
  const context = useContext(WishlistContext)
  if (!context) {
    throw new Error('useWishlist는 WishlistProvider 안에서만 사용할 수 있습니다.')
  }
  return context
}
