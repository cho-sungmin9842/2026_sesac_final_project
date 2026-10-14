import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

// 페이지 이동/전환(라우트 경로 또는 쿼리스트링 변경)마다 항상 화면 맨 위로 스크롤합니다. App.jsx에 한
// 번만 마운트해서, 여러 페이지에 각자 흩어져 있던 개별 window.scrollTo(0, 0) 처리를 여기 하나로
// 통일합니다(사이드바 장르 필터처럼 URL이 안 바뀌는 순수 로컬 상태 변경은 각 페이지가 그대로 따로
// 처리합니다 - 이건 "페이지 이동"이 아니기 때문입니다).
function ScrollToTop() {
  const { pathname, search } = useLocation()

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname, search])

  return null
}

export default ScrollToTop
