// 장르: KMDB의 genre 검색 파라미터에 그대로 전달합니다(부분일치로 동작 확인됨).
// 목록: 2026-09-21에 전체 카탈로그 15,000건을 샘플링해 실제로 등장하는 장르 태그 중
// 빈도가 높고 사용자에게 의미 있는 장르 위주로 골랐습니다(등장 빈도 순).
export const genres = [
  '전체',
  '드라마',
  '코메디',
  '액션',
  '스릴러',
  '범죄',
  '어드벤처',
  '판타지',
  'SF',
  '공포',
  '로맨스',
  '미스터리',
  '애니메이션',
]

// KMDB 실제 장르 태그값은 "코메디"라서 필터/검색 파라미터·저장값은 그대로 두고, 화면에 보여줄 표기만 맞춤법에 맞게 바꿉니다.
const GENRE_LABELS = { 코메디: '코미디' }
export function genreLabel(genre) {
  return GENRE_LABELS[genre] ?? genre
}
// 개봉연도: KMDB에 "제작연도" 파라미터가 없어 개봉일자 범위(releaseDts~releaseDte)로 변환해 보냅니다.
// 2026-09-21에 전체 카탈로그(121,326건)를 빠짐없이 스캔해서 실제 존재하는 연도만 나열합니다(1885~2027).
// 이 범위 안에서도 1886·1888·1890·1891·1892년은 데이터가 아예 없어서 목록에서 뺐습니다.
const YEAR_RANGE_START = 2027
const YEAR_RANGE_END = 1885
const YEARS_WITH_NO_DATA = new Set([1886, 1888, 1890, 1891, 1892])

export const years = [
  '전체',
  ...Array.from({ length: YEAR_RANGE_START - YEAR_RANGE_END + 1 }, (_, i) => YEAR_RANGE_START - i)
    .filter((year) => !YEARS_WITH_NO_DATA.has(year))
    .map(String),
]
// 러닝타임: KMDB 검색 API에 관련 파라미터가 없어 현재 페이지에 불러온 결과에만 클라이언트에서 적용합니다.
export const runtimeFilters = ['전체', '2시간 미만', '2시간 이상']
