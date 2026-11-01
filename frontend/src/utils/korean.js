// 단어의 받침 유무에 따라 "을/를", "이/가", "은/는", "과/와", "으로/로" 같은 조사를 자동으로 골라줍니다.
// 그동안 "아이디을(를) 입력해주세요."처럼 "을(를)" 표기를 그대로 화면에 띄워버린 곳이 여러 군데
// 있었는데(2026-11-01 실측), 한글 음절의 유니코드 코드포인트로 종성(받침) 유무를 계산해 매번 정확한
// 조사를 붙이도록 공통 유틸로 뽑았습니다.
const HANGUL_SYLLABLE_START = 0xac00
const HANGUL_SYLLABLE_END = 0xd7a3

function hasBatchim(word) {
  if (!word) return false
  const lastChar = word.trim().slice(-1)
  const code = lastChar.charCodeAt(0)
  // 한글 음절이 아니면(영문/숫자/기타 문자로 끝나면) 받침이 없는 것으로 취급합니다.
  if (code < HANGUL_SYLLABLE_START || code > HANGUL_SYLLABLE_END) {
    return false
  }
  return (code - HANGUL_SYLLABLE_START) % 28 !== 0
}

// josa('아이디', ['을', '를']) -> '를', josa('선생님', ['을', '를']) -> '을'
export function josa(word, [withBatchim, withoutBatchim]) {
  return hasBatchim(word) ? withBatchim : withoutBatchim
}
