// KMDB는 예고편/캐릭터영상/메이킹/인터뷰 등 모든 클립을 vods(trailers) 배열 하나에 순서 구분 없이 섞어서 내려줍니다.
// 상세 페이지뿐 아니라 목록/검색/홈 등 카드형 포스터의 호버 예고편 버튼도 이 로직을 그대로 씁니다.
const TRAILER_KEYWORDS = ['예고편', 'trailer']
// 오래된 항목은 "예고편"이라는 말 없이 "1차"/"공식2차"/"메인"처럼 회차만 적혀있는 경우가 있습니다(예: 인터스텔라).
const TRAILER_SHORT_LABEL_PATTERN = /^(\d+차|공식\s*\d*차|메인)$/

function bracketContent(label) {
  return label?.match(/\[(.+)\]/)?.[1]?.trim() ?? null
}

function isTrailerLabel(label) {
  const lower = label?.toLowerCase() ?? ''
  if (TRAILER_KEYWORDS.some((keyword) => lower.includes(keyword))) return true
  const bracket = bracketContent(label)
  return bracket ? TRAILER_SHORT_LABEL_PATTERN.test(bracket) : false
}

function findClip(trailers, isMatch) {
  return trailers?.find((clip) => clip.url && isMatch(clip.label))?.url ?? null
}

// 포스터 호버 버튼은 실제로 "예고편"류(vodClass가 예고편/trailer 등)인 클립이 있을 때만 보여줍니다.
// 메이킹/인터뷰 등 나머지 영상은 OtherVideoButtons가 따로 버튼으로 보여주므로 여기서는 대체하지 않습니다.
export function pickVideo(trailers) {
  const trailerUrl = findClip(trailers, isTrailerLabel)
  return trailerUrl ? { url: trailerUrl, kind: 'trailer' } : null
}

// 예고편(vodClass가 "예고편"류)이 아닌 나머지 vods(메이킹, 인터뷰, TV스팟 등)를 각각 버튼으로 보여주기 위한 목록.
// 포스터 호버 버튼에 이미 쓰인 링크(selectedVideo)는 중복 노출을 막기 위해 제외합니다.
export function pickOtherVideos(trailers, selectedVideo) {
  if (!trailers) return []

  const seen = new Set()
  const others = []
  for (const clip of trailers) {
    if (!clip.url || isTrailerLabel(clip.label)) continue
    if (selectedVideo?.url === clip.url) continue
    if (seen.has(clip.url)) continue
    seen.add(clip.url)
    others.push({ label: bracketContent(clip.label) ?? clip.label ?? '관련 영상', url: clip.url })
  }
  return others
}
