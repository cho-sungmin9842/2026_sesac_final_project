const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

// 로그인/리뷰/예매/다운로드처럼 "누가 요청했는지" 알아야 하는 API에서 공통으로 씁니다.
// 정식 토큰 인증 대신, 로그인된 사용자의 id를 X-User-Id 헤더로 실어 보내는 데모 수준의 인증입니다.
export default async function request(path, { method = 'GET', body, userId } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (userId != null) headers['X-User-Id'] = String(userId)

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    throw new Error(errorBody?.message ?? `요청이 실패했습니다 (status ${response.status})`)
  }

  if (response.status === 204) return null
  const text = await response.text()
  return text ? JSON.parse(text) : null
}
