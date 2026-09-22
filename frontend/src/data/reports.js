// 관리자 - 리뷰 신고 관리 화면(6p)용 목업 데이터.

export const reportStats = {
  pending: 12,
  pendingDelta: 3,
  resolvedToday: 28,
  suspendedThisWeek: 2,
  newToday: 12,
}

export const reports = [
  { id: 1, movie: '기생충', content: '"결말은 ○○가 죽고 ○○가..."', reason: '스포일러', status: '대기' },
  { id: 2, movie: '범죄도시4', content: '광고성 링크 포함 리뷰', reason: '스팸/도배', status: '검토중' },
  { id: 3, movie: '파묘', content: '욕설 및 비방 표현 포함', reason: '욕설', status: '대기' },
  { id: 4, movie: '듄: 파트2', content: '"엔딩 반전이 사실은..."', reason: '스포일러', status: '처리완료' },
  { id: 5, movie: '서울의 봄', content: '동일 계정 반복 도배 리뷰', reason: '스팸/도배', status: '처리완료' },
]
