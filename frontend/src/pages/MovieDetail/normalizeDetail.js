// 백엔드(KMDB 연동) 응답을 화면에서 쓰기 편한 모양으로 정리합니다.

// KMDB는 예고편/캐릭터영상/메이킹/인터뷰 등 모든 클립을 vods 배열 하나에 순서 구분 없이 섞어서 내려줍니다.
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
function pickVideo(trailers) {
  const trailerUrl = findClip(trailers, isTrailerLabel)
  if (trailerUrl) return { url: trailerUrl, kind: 'trailer' }

  const makingUrl = findClip(trailers, isMakingLabel)
  if (makingUrl) return { url: makingUrl, kind: 'making' }

  return null
}

export function normalizeDetail(dto) {
  return {
    title: dto.title,
    year: dto.year,
    genres: dto.genres ?? [],
    runtimeMinutes: dto.runtimeMinutes,
    ageRating: dto.ageRating,
    directorsLabel: dto.directors?.length ? dto.directors.join(', ') : '정보 없음',
    castLabel: dto.actors?.length ? dto.actors.slice(0, 6).map((actor) => actor.name).join(', ') : '정보 없음',
    tags: dto.keywords?.slice(0, 5) ?? [],
    plot: dto.plot ?? '줄거리 정보가 없습니다.',
    posterUrl: dto.posterUrl ?? null,
    video: pickVideo(dto.trailers),
    aiSummary: null,
  }
}
