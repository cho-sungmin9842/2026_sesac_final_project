// 장르: KMDB의 genre 검색 파라미터에 그대로 전달합니다(부분일치로 동작 확인됨).
// KMDB가 실제로 쓰는 장르 태그 전체 목록입니다.
export const genres = [
  '전체',
  'SF',
  '가족',
  '공포',
  '교육',
  '기업ㆍ기관ㆍ단체',
  '동성애',
  '드라마',
  '로드무비',
  '멜로/로맨스',
  '무협',
  '문화',
  '뮤직',
  '미스터리',
  '범죄',
  '사회',
  '사회물(경향)',
  '스릴러',
  '스포츠',
  '시대극/사극',
  '실험',
  '아동',
  '액션',
  '어드벤처',
  '에로',
  '역사',
  '옴니버스',
  '인권',
  '인물',
  '자연ㆍ환경',
  '재난',
  '전쟁',
  '지역',
  '청춘영화',
  '코메디',
  '판타지',
  '하이틴(고교)',
]

export function genreLabel(genre) {
  return genre
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
