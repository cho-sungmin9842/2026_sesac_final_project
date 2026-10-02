import { Link, useSearchParams } from 'react-router-dom'

// 사용자가 결제창에서 결제를 취소했거나, 토스페이먼츠 쪽에서 결제 자체가 거절된 경우 이 페이지로 돌아옵니다.
function PaymentFailPage() {
  const [searchParams] = useSearchParams()

  return (
    <div className="mx-auto max-w-lg px-6 py-16 text-center">
      <p className="text-lg font-bold text-rose-400">결제가 취소되었거나 실패했습니다</p>
      <p className="mt-2 text-sm text-gray-400">{searchParams.get('message') ?? '알 수 없는 오류가 발생했습니다.'}</p>
      <Link to="/booking" className="mt-6 inline-block text-indigo-400 hover:underline">
        예매 목록으로 돌아가기
      </Link>
    </div>
  )
}

export default PaymentFailPage
