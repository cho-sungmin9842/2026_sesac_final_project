import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { confirmAndCreateBooking } from '../../api/paymentApi'

// 토스페이먼츠 결제창에서 결제를 마치면 이 페이지로 돌아옵니다(paymentKey/orderId/amount가 쿼리스트링으로
// 붙어서 옵니다). 결제를 요청하기 직전에 BookingSeatPage가 sessionStorage에 저장해둔 "이 결제가 어떤
// 예매인지"를 orderId로 꺼내서, 백엔드에 결제 승인 + 예매 생성을 요청합니다.
function PaymentSuccessPage() {
  const [searchParams] = useSearchParams()
  const { user } = useAuth()
  const [status, setStatus] = useState('confirming') // confirming | done | error
  const [errorMessage, setErrorMessage] = useState('')
  const [booking, setBooking] = useState(null)
  const requestedRef = useRef(false)

  useEffect(() => {
    // React StrictMode(개발 모드)에서 effect가 두 번 실행돼도 결제 승인 요청은 한 번만 나가야 합니다.
    if (requestedRef.current) return
    requestedRef.current = true

    const paymentKey = searchParams.get('paymentKey')
    const orderId = searchParams.get('orderId')
    const amount = Number(searchParams.get('amount'))
    const raw = orderId ? sessionStorage.getItem(`booking_intent_${orderId}`) : null

    if (!paymentKey || !orderId || !raw) {
      setStatus('error')
      setErrorMessage('결제 정보를 찾을 수 없습니다. 예매를 처음부터 다시 진행해주세요.')
      return
    }

    const intent = JSON.parse(raw)
    confirmAndCreateBooking(user.id, {
      paymentKey,
      orderId,
      amount,
      screeningId: intent.screeningId,
      seatIds: intent.seatIds,
      ticketCounts: intent.ticketCounts,
    })
      .then((dto) => {
        sessionStorage.removeItem(`booking_intent_${orderId}`)
        setBooking(dto)
        setStatus('done')
      })
      .catch((error) => {
        setStatus('error')
        setErrorMessage(error.message)
      })
  }, [searchParams, user.id])

  return (
    <div className="mx-auto max-w-lg px-6 py-16 text-center">
      {status === 'confirming' && <p className="text-gray-300">결제를 확인하는 중입니다...</p>}

      {status === 'error' && (
        <>
          <p className="text-lg font-bold text-rose-400">예매에 실패했습니다</p>
          <p className="mt-2 text-sm text-gray-400">{errorMessage}</p>
          <Link to="/booking" className="mt-6 inline-block text-indigo-400 hover:underline">
            예매 목록으로 돌아가기
          </Link>
        </>
      )}

      {status === 'done' && booking && (
        <>
          <p className="text-lg font-bold text-white">예매가 완료되었습니다 🎉</p>
          <div className="mt-4 rounded-lg bg-slate-900 p-4 text-left text-sm text-gray-300">
            <p className="font-semibold text-white">{booking.movieTitle}</p>
            <p className="mt-1">
              {booking.theaterName} · {booking.showDate} {booking.showtime}
            </p>
            <p className="mt-1">좌석: {booking.seats.join(', ')}</p>
            <p className="mt-1">결제금액: {booking.totalPrice.toLocaleString()}원</p>
          </div>
          <Link to="/booking" className="mt-6 inline-block text-indigo-400 hover:underline">
            예매 목록으로 돌아가기
          </Link>
        </>
      )}
    </div>
  )
}

export default PaymentSuccessPage
