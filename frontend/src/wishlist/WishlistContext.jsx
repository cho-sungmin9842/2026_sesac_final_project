import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { addWishlist, getWishlist, removeWishlist } from '../api/wishlistApi'

// 어느 화면의 영화 포스터 카드든 찜 여부를 한 번만 불러와 공유하기 위한 전역 상태입니다.
// (카드마다 개별적으로 isWishlisted를 호출하면 목록 화면에서 N+1 호출이 발생합니다.)
const WishlistContext = createContext(null)

export function WishlistProvider({ children }) {
  const { user } = useAuth()
  const [wishlistedIds, setWishlistedIds] = useState(new Set())

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
      } catch (error) {
        // 요청이 실패하면 화면에 반영했던 낙관적 업데이트를 되돌립니다.
        setWishlistedIds((prev) => {
          const next = new Set(prev)
          if (wasWishlisted) next.add(id)
          else next.delete(id)
          return next
        })
        window.alert(error.message)
      }
    },
    [user, wishlistedIds],
  )

  return <WishlistContext.Provider value={{ wishlistedIds, toggle, refresh }}>{children}</WishlistContext.Provider>
}

export function useWishlist() {
  const context = useContext(WishlistContext)
  if (!context) {
    throw new Error('useWishlist는 WishlistProvider 안에서만 사용할 수 있습니다.')
  }
  return context
}
