// 리뷰 신고 사유 목록입니다. key는 백엔드 ReviewReportReason enum의 name()과 반드시 같아야 합니다.
export const REVIEW_REPORT_REASONS = [
  { key: 'ABUSIVE_LANGUAGE', label: '욕설·비방·혐오 표현' },
  { key: 'SPAM_AD', label: '스팸/광고성 내용' },
  { key: 'SPOILER', label: '스포일러 포함' },
  { key: 'FALSE_INFO', label: '허위 정보 또는 영화와 무관한 내용' },
]
