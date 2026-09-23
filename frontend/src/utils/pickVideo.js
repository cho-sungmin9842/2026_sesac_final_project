// KMDB는 예고편/캐릭터영상/메이킹/인터뷰 등 모든 클립을 vods(trailers) 배열 하나에 순서 구분 없이 섞어서 내려줍니다.
// 상세 페이지뿐 아니라 목록/검색/홈 등 카드형 포스터의 호버 예고편 버튼도 이 로직을 그대로 씁니다.
const TRAILER_KEYWORDS = ['예고편', 'trailer']
// 오래된 항목은 "예고편"이라는 말 없이 "1차"/"공식2차"/"메인"처럼 회차만 적혀있는 경우가 있습니다(예: 인터스텔라).
const TRAILER_SHORT_LABEL_PATTERN = /^(\d+차|공식\s*\d*차|메인)$/
const MAKING_KEYWORDS = ['메이킹', '제작기', '비하인드', 'making']

function bracketContent(label) {
  return label?.match(/\[(.+)\]/)?.[1]?.trim() ?? null
}

function isTrailerLabel(label) {
  const lower = label?.toLowerCase() ?? ''
  if (TRAILER_KEYWORDS.some((keyword) => lower.includes(keyword))) return true
  const bracket = bracketContent(label)
  return bracket ? TRAILER_SHORT_LABEL_PATTERN.test(bracket) : false
}

function isMakingLabel(label) {
  const lower = label?.toLowerCase() ?? ''
  return MAKING_KEYWORDS.some((keyword) => lower.includes(keyword))
}

function findClip(trailers, isMatch) {
  return trailers?.find((clip) => clip.url && isMatch(clip.label))?.url ?? null
}

// 예고편이 있으면 예고편을, 없으면 메이킹류 영상을, 그마저 없으면 null을 돌려줍니다.
export function pickVideo(trailers) {
  const trailerUrl = findClip(trailers, isTrailerLabel)
  if (trailerUrl) return { url: trailerUrl, kind: 'trailer' }

  const makingUrl = findClip(trailers, isMakingLabel)
  if (makingUrl) return { url: makingUrl, kind: 'making' }

  // 최근 소규모 개봉작은 vodClass에 "예고편" 같은 구분 표시 없이 영화 제목만 그대로 붙어 있는 경우가 많습니다
  // (예: vodClass="콘크리트 녹색섬"). 그래도 vodUrl 자체는 실제 재생 가능한 링크라서, 라벨로 구분이 안 되면
  // 첫 번째로 발견되는 링크를 예고편으로 간주합니다.
  const firstUrl = trailers?.find((clip) => clip.url)?.url ?? null
  if (firstUrl) return { url: firstUrl, kind: 'trailer' }

  return null
}
