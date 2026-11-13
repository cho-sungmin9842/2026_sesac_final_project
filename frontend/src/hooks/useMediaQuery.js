import { useEffect, useState } from 'react'

// Tailwind의 반응형 클래스(md:hidden 등)는 CSS로 display만 바꿀 뿐 컴포넌트를 마운트 해제하진
// 않습니다 - 데스크톱/모바일 레이아웃에 각각 넣어둔 컴포넌트가 화면 폭과 무관하게 항상 둘 다
// 마운트된 채로 남아서, 폴링 타이머가 있는 컴포넌트(NotificationBell 등)는 매번 두 배로 돌게
// 됩니다. 실제 화면 폭을 보고 조건부 렌더링해서 한쪽만 마운트하려는 용도로 씁니다.
function useMediaQuery(query) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches)

  useEffect(() => {
    const mediaQueryList = window.matchMedia(query)
    const handleChange = (event) => setMatches(event.matches)
    setMatches(mediaQueryList.matches)
    mediaQueryList.addEventListener('change', handleChange)
    return () => mediaQueryList.removeEventListener('change', handleChange)
  }, [query])

  return matches
}

export default useMediaQuery
