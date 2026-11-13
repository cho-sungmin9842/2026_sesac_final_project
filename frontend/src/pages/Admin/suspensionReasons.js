// 관리자 "회원 관리" 화면에서 정지시킬 때 고르는 사유 목록입니다. key는 백엔드
// SuspensionReason enum의 name()과 반드시 같아야 합니다.
export const SUSPENSION_REASONS = [
  { key: 'POLICY_VIOLATION', category: '이용 정책 위반', detail: '욕설·비방·혐오 표현 리뷰' },
  { key: 'FRAUDULENT_USE', category: '부정 이용', detail: '매크로/자동화 좌석 선점·예매 반복' },
  { key: 'PAYMENT_BOOKING', category: '결제/예매 관련', detail: '상습 노쇼' },
  { key: 'CHATBOT_ABUSE', category: 'AI 챗봇/시스템 어뷰징', detail: '악의적 프롬프트 반복 입력' },
  { key: 'LEGAL_SAFETY', category: '법적/안전 문제', detail: '저작권 침해 게시물 유포' },
]
