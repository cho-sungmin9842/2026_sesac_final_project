import { useEffect, useRef, useState } from 'react'

// 토스페이먼츠가 공식 문서/샘플 프로젝트(github.com/tosspayments/tosspayments-sample)에서 누구나 테스트하도록
// 공개해둔 결제위젯 클라이언트 키입니다(실결제 불가). 백엔드 application.yml의 시크릿 키와 같은 세트입니다.
const CLIENT_KEY = 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm'

function generateKey() {
  return window.btoa(String(Math.random())).replace(/[^a-zA-Z0-9]/g, '').slice(0, 20)
}

/**
 * 토스페이먼츠 결제위젯(v2 SDK) UI만 사용합니다 - 결제수단/약관 선택 화면은 실제 위젯을 그대로 그려서
 * 보여주지만, "결제하기"를 눌렀을 때 토스의 실제 결제창으로 리다이렉트하지는 않습니다(테스트/데모 목적상
 * 그 자리에서 바로 완료 처리). 실제 승인까지 테스트해보고 싶다면 widgets.requestPayment()를 다시 연결하면 됩니다.
 */
function TossPaymentWidget({ amount, onComplete }) {
  const widgetsRef = useRef(null)
  // React 18 StrictMode(개발 모드)는 effect를 마운트→클린업→마운트로 두 번 실행합니다. 이 ref로 실제
  // 초기화가 컴포넌트 인스턴스당 한 번만 실행되게 막습니다(가드가 없으면 같은 #toss-payment-method에
  // renderPaymentMethods가 두 번 겹쳐 호출되어 하나는 성공하고 다른 하나는 충돌로 실패합니다).
  const initStartedRef = useRef(false)
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (initStartedRef.current) return
    initStartedRef.current = true

    async function init() {
      if (!window.TossPayments) {
        setStatus('error')
        return
      }
      try {
        const tossPayments = window.TossPayments(CLIENT_KEY)
        const widgets = tossPayments.widgets({ customerKey: generateKey() })
        await widgets.setAmount({ currency: 'KRW', value: amount })
        await Promise.all([
          widgets.renderPaymentMethods({ selector: '#toss-payment-method', variantKey: 'DEFAULT' }),
          widgets.renderAgreement({ selector: '#toss-agreement', variantKey: 'AGREEMENT' }),
        ])
        widgetsRef.current = widgets
        setStatus('ready')
      } catch {
        setStatus('error')
      }
    }

    init()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 좌석/인원을 바꿔서 금액이 달라지면 이미 그려둔 위젯에도 반영합니다.
  useEffect(() => {
    if (widgetsRef.current) {
      widgetsRef.current.setAmount({ currency: 'KRW', value: amount })
    }
  }, [amount])

  const handleComplete = async () => {
    if (status !== 'ready' || submitting) return
    setSubmitting(true)
    try {
      await onComplete()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="rounded-lg border border-gray-200 p-4">
      {status === 'error' && (
        <p className="text-sm text-rose-500">결제 SDK를 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.</p>
      )}
      <div id="toss-payment-method" />
      <div id="toss-agreement" />
      <button
        type="button"
        disabled={status !== 'ready' || submitting}
        onClick={handleComplete}
        className="mt-4 w-full rounded-lg bg-blue-600 px-6 py-3 text-sm font-semibold text-white hover:bg-blue-500 disabled:cursor-not-allowed disabled:bg-gray-300"
      >
        {status !== 'ready' ? '결제 수단을 불러오는 중...' : submitting ? '처리 중...' : `${amount.toLocaleString()}원 결제하기`}
      </button>
    </div>
  )
}

export default TossPaymentWidget
