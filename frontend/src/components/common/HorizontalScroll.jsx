import { useEffect, useRef, useState } from 'react'

// overflow-x-auto 영역은 모바일에서 스크롤바가 기본적으로 숨겨져 있어서, 안의 내용이 화면보다
// 넓어도 "옆으로 더 있다"는 걸 알아챌 방법이 없습니다(관리자 화면의 표/차트/좌석맵이 전부 이 패턴이라
// 375px 같은 좁은 화면에서 오른쪽 컬럼이 통째로 안 보이는 일이 있었습니다). 오른쪽 끝에 흐려지는
// 그라데이션을 덧씌워 "더 있다"는 걸 알려주고, 끝까지 스크롤하면 자연스럽게 사라집니다.
function HorizontalScroll({ children, className = '' }) {
  const scrollRef = useRef(null)
  const [canScrollMore, setCanScrollMore] = useState(false)

  useEffect(() => {
    const el = scrollRef.current
    if (!el) return undefined

    const checkScroll = () => {
      setCanScrollMore(el.scrollWidth - el.scrollLeft - el.clientWidth > 4)
    }

    checkScroll()
    el.addEventListener('scroll', checkScroll)
    window.addEventListener('resize', checkScroll)
    const resizeObserver = new ResizeObserver(checkScroll)
    resizeObserver.observe(el)

    return () => {
      el.removeEventListener('scroll', checkScroll)
      window.removeEventListener('resize', checkScroll)
      resizeObserver.disconnect()
    }
  }, [children])

  return (
    <div className="relative">
      <div ref={scrollRef} className={`overflow-x-auto ${className}`}>
        {children}
      </div>
      {canScrollMore && (
        <div className="pointer-events-none absolute inset-y-0 right-0 w-10 bg-gradient-to-l from-slate-900 to-transparent" />
      )}
    </div>
  )
}

export default HorizontalScroll
