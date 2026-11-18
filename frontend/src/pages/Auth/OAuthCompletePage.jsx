import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { exchangeOAuthToken } from '../../api/authApi'

// 구글 로그인 콜백(OAuthController)이 마지막에 브라우저를 이 화면으로 돌려보냅니다.
// 성공하면 token 쿼리파라미터를, 실패하면 error 쿼리파라미터를 받습니다 - token은 60초짜리 1회용이라
// 이 화면이 뜨자마자 바로 소모해서 실제 로그인된 사용자 정보로 바꿔야 합니다(httpClient.js의
// X-User-Id 인증 방식이라 쿠키/세션처럼 서버가 알아서 로그인 상태를 유지해주지 않습니다).
function OAuthCompletePage() {
  const [searchParams] = useSearchParams()
  const { setSession } = useAuth()
  const navigate = useNavigate()
  const [errorMessage, setErrorMessage] = useState('')
  const hasRun = useRef(false)

  useEffect(() => {
    if (hasRun.current) return
    hasRun.current = true

    const token = searchParams.get('token')
    const error = searchParams.get('error')

    if (error) {
      setErrorMessage(error)
      return
    }
    if (!token) {
      setErrorMessage('잘못된 접근입니다.')
      return
    }

    exchangeOAuthToken(token)
      .then((user) => {
        setSession(user)
        navigate(user.isAdmin ? '/admin/reports' : '/', { replace: true })
      })
      .catch((err) => setErrorMessage(err.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="flex min-h-svh items-center justify-center bg-slate-100 px-6">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 text-center shadow">
        {errorMessage ? (
          <>
            <p className="text-sm font-semibold text-red-600">⚠ {errorMessage}</p>
            <button
              type="button"
              onClick={() => navigate('/login', { replace: true })}
              className="mt-5 w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              로그인 화면으로 돌아가기
            </button>
          </>
        ) : (
          <p className="text-sm font-medium text-slate-600">로그인 처리 중입니다...</p>
        )}
      </div>
    </div>
  )
}

export default OAuthCompletePage
