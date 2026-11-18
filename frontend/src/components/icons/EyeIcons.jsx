// 비밀번호 입력란의 보기/숨기기 토글 아이콘입니다(로그인/회원가입 양쪽에서 공용으로 씁니다).

export function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M1.5 12S5 5 12 5s10.5 7 10.5 7-3.5 7-10.5 7S1.5 12 1.5 12z" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

export function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M3 3l18 18" strokeLinecap="round" />
      <path
        d="M9.9 5.2A10.4 10.4 0 0 1 12 5c7 0 10.5 7 10.5 7a13.4 13.4 0 0 1-3.1 4M6.6 6.6C3.6 8.5 1.5 12 1.5 12s3.5 7 10.5 7a10.4 10.4 0 0 0 4.1-.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M9.9 14.1a3 3 0 0 0 4.2-4.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
