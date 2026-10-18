const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

// 로그인/리뷰/예매/다운로드처럼 "누가 요청했는지" 알아야 하는 API에서 공통으로 씁니다.
// 정식 토큰 인증 대신, 로그인된 사용자의 id를 X-User-Id 헤더로 실어 보내는 데모 수준의 인증입니다.
export default async function request(path, { method = 'GET', body, userId, keepalive } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (userId != null) headers['X-User-Id'] = String(userId)

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    // keepalive: 페이지를 떠나는 순간(beforeunload/pagehide)에 쏘는 요청에 씁니다 - 보통의 fetch는
    // 문서가 언로드되면 응답을 기다리지 않고 바로 취소되는데, keepalive를 주면 브라우저가 페이지가
    // 닫힌 뒤에도 이 요청만은 짧게 이어서 보내줍니다(좌석 선점 해제처럼 "꼭 전달은 돼야 하는" 요청).
    ...(keepalive ? { keepalive: true } : {}),
  })

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    throw new Error(errorBody?.message ?? `요청이 실패했습니다 (status ${response.status})`)
  }

  if (response.status === 204) return null
  const text = await response.text()
  return text ? JSON.parse(text) : null
}
