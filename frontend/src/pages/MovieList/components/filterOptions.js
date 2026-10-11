// 장르 목록은 더 이상 여기 하드코딩하지 않고, 백엔드 MovieGenres.ALL을 유일한 출처로 삼아
// api/movieApi.js의 getGenres()로 받아옵니다(ChatService의 Gemini 프롬프트와도 같은 출처라
// 한쪽만 바뀌어 어긋나는 일이 없습니다). "전체"는 화면 전용 선택지라 쓰는 쪽에서 덧붙입니다.

export function genreLabel(genre) {
  return genre
}
// 개봉연도: KMDB에 "제작연도" 파라미터가 없어 개봉일자 범위(releaseDts~releaseDte)로 변환해 보냅니다.
// 2026-09-21에 전체 카탈로그(121,326건)를 빠짐없이 스캔해서 실제 존재하는 연도만 나열합니다(1885~2027).
// 이 범위 안에서도 1886·1888·1890·1891·1892년은 데이터가 아예 없어서 목록에서 뺐습니다.
const YEAR_RANGE_START = 2027
const YEAR_RANGE_END = 1885
const YEARS_WITH_NO_DATA = new Set([1886, 1888, 1890, 1891, 1892])

// "정보 없음"은 개봉일자도 제작연도도 아예 없어 연도 자체를 알 수 없는 극소수 영화만 모아 보여줍니다
// (러닝타임의 "정보 없음"과 같은 개념 - movieApi.js가 "unknown" 코드로 바꿔 보냅니다).
export const years = [
  '전체',
  ...Array.from({ length: YEAR_RANGE_START - YEAR_RANGE_END + 1 }, (_, i) => YEAR_RANGE_START - i)
    .filter((year) => !YEARS_WITH_NO_DATA.has(year))
    .map(String),
  '정보 없음',
]
// 러닝타임: KMDB 검색 API에 관련 파라미터가 없어, 백엔드가 응답을 받은 뒤 직접 걸러서 내려줍니다
// (totalCount/페이지네이션에도 반영됨 - movieApi.js가 이 라벨을 under120/over120/unknown 코드로 바꿔
// 보냅니다). "정보 없음"은 KMDB에 러닝타임 자체가 없는 영화만 모아 보여줍니다.
export const runtimeFilters = ['전체', '2시간 미만', '2시간 이상', '정보 없음']
