import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import {
  bulkDeleteReviewsAsAdmin,
  deleteReviewAsAdmin,
  getAdminReviewFilterOptions,
  getAdminReviewIds,
  getAdminReviewStats,
  getAllReviewsForAdmin,
  getReviewDeletionLogs,
} from '../../api/reviewApi'
import ReportTable from './components/ReportTable'
import ReviewDetailDialog from './components/ReviewDetailDialog'
import ReviewReportsDialog from './components/ReviewReportsDialog'
import ReviewFilterBar, { EMPTY_REVIEW_FILTERS } from './components/ReviewFilterBar'
import DeletionLogFilterBar, { EMPTY_LOG_FILTERS } from './components/DeletionLogFilterBar'
import DeletionLogTable from './components/DeletionLogTable'
import DeletionLogDetailDialog from './components/DeletionLogDetailDialog'
import StatCard from './components/StatCard'
import ConfirmDialog from '../../components/common/ConfirmDialog'
import InfoDialog from '../../components/common/InfoDialog'
import SortDropdown from '../MovieList/components/SortDropdown'
import Pagination from '../../components/common/Pagination'

const REVIEW_SORT_OPTIONS = [
  { value: 'latest', label: '최신순' },
  { value: 'rating', label: '평점순' },
  { value: 'reportCount', label: '신고 많은 순' },
]

// 삭제 이력은 신고 수를 따로 집계해두지 않고(삭제 당시의 신고 사유만 문자열로 남김), 신고 사유가
// 몇 가지였는지(reportReasons를 ", "로 나눈 개수)로 "신고 많은 순"에 해당하는 정렬을 대신합니다.
const LOG_SORT_OPTIONS = [
  { value: 'latest', label: '최신순(삭제일)' },
  { value: 'rating', label: '평점순' },
  { value: 'reportReasonCount', label: '신고 사유 많은 순' },
]

const PAGE_SIZE = 10
const LOG_PAGE_SIZE = 10

// "삭제 이력" 탭 필터 - 이미 전체를 불러온 deletionLogs를 클라이언트에서 거릅니다(리뷰 목록과 달리
// 서버 페이지네이션으로 옮길 만큼 쌓이지 않아 그대로 둠). reportReasons가 없으면(null) 관리자가
// 직접 지운 것, 있으면 신고로 지워진 것입니다(ReviewDeletionLog 참고).
function matchesLogFilters(log, filters) {
  if (filters.movieTitle && log.movieTitle !== filters.movieTitle) return false
  if (filters.authorNickname && log.reviewAuthorNickname !== filters.authorNickname) return false
  if (filters.reportedOnly && !log.reportReasons) return false
  const deletedDate = log.deletedAt.slice(0, 10)
  if (filters.dateFrom && deletedDate < filters.dateFrom) return false
  if (filters.dateTo && deletedDate > filters.dateTo) return false
  return true
}

// 새로고침하거나 링크를 복사해 보내도 지금 보던 조건(탭/페이지/정렬/필터) 그대로 다시 열리도록, 이
// 값들을 주소창 쿼리에서 읽어옵니다(2026-11-24 요청). 기본값과 같은 항목은 아예 쿼리에 안 남기므로,
// 없는 값은 전부 기본값으로 해석합니다.
function parseReviewFiltersFromParams(params) {
  return {
    movieTitle: params.get('movieTitle') ?? '',
    authorNickname: params.get('authorNickname') ?? '',
    reportedOnly: params.get('reportedOnly') === 'true',
    dateFrom: params.get('dateFrom') ?? '',
    dateTo: params.get('dateTo') ?? '',
    content: params.get('content') ?? '',
  }
}

// 관리자 "리뷰 관리" 화면 - 검색/정렬/페이지네이션은 전부 서버에서 처리합니다(리뷰가 많아지면
// 클라이언트에서 전체를 들고 거르는 방식이 느려지므로 2026-11-21에 옮김). 신고된 리뷰는 reportCount로
// 표시되고, "신고 N건" 뱃지를 누르면 ReviewReportsDialog에서 신고 사유/신고자까지 보고 그 자리에서
// 삭제·삭제취소를 처리할 수 있습니다.
function AdminReportsPage() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const [reviews, setReviews] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [stats, setStats] = useState(null) // { totalCount, todayCount } - 검색/필터와 무관한 전체 집계
  const [statsStatus, setStatsStatus] = useState('loading') // loading | ready | error
  const [filterOptionPairs, setFilterOptionPairs] = useState([]) // [{ movieTitle, authorNickname }, ...]
  const [matchingIds, setMatchingIds] = useState([]) // 지금 필터에 맞는 리뷰 전체의 id ("전체 선택"용)
  const [pendingDeleteReviewId, setPendingDeleteReviewId] = useState(null)
  const [pendingBulkDelete, setPendingBulkDelete] = useState(false)
  const [deleteErrorMessage, setDeleteErrorMessage] = useState('')
  const [reviewSort, setReviewSort] = useState(() => searchParams.get('sort') ?? 'latest')
  const [filters, setFilters] = useState(() => parseReviewFiltersFromParams(searchParams))
  const [page, setPage] = useState(() => {
    const fromUrl = Number(searchParams.get('page'))
    return Number.isInteger(fromUrl) && fromUrl > 0 ? fromUrl : 1
  })
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [reportsDialogReviewId, setReportsDialogReviewId] = useState(null)
  const [detailReview, setDetailReview] = useState(null)
  const [activeTab, setActiveTab] = useState(() => (searchParams.get('tab') === 'logs' ? 'logs' : 'reviews')) // 'reviews' | 'logs'
  const [deletionLogs, setDeletionLogs] = useState([])
  const [logsStatus, setLogsStatus] = useState('idle') // idle | loading | ready | error
  const [logsPage, setLogsPage] = useState(1)
  const [logFilters, setLogFilters] = useState(EMPTY_LOG_FILTERS)
  const [logSort, setLogSort] = useState('latest')
  const [detailLog, setDetailLog] = useState(null)

  // 페이지를 빠르게 넘기거나 필터를 연달아 바꿀 때, 먼저 보낸 느린 요청이 나중에 보낸 요청보다
  // 늦게 돌아와 화면을 덮어쓰는 걸 막기 위한 "가장 최근 요청" 표시입니다. 리뷰 목록 조회뿐 아니라
  // "전체 선택" id 목록·필터 드롭다운 선택지 조회에도 똑같이 적용합니다 - 필터를 빠르게 연달아
  // 바꾸면 이 요청들도 응답 순서가 뒤바뀔 수 있습니다(2026-11-24 요청 전까지는 리뷰 목록에만 있었음).
  const fetchIdRef = useRef(0)
  const matchingIdsRequestRef = useRef(0)
  const filterOptionsRequestRef = useRef(0)

  const loadReviews = () => {
    const requestId = ++fetchIdRef.current
    setStatus('loading')
    getAllReviewsForAdmin(user.id, {
      page: page - 1,
      size: PAGE_SIZE,
      sort: reviewSort,
      movieTitle: filters.movieTitle,
      authorNickname: filters.authorNickname,
      reportedOnly: filters.reportedOnly,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      content: filters.content,
    })
      .then((data) => {
        if (fetchIdRef.current !== requestId) return
        // 삭제 등으로 전체 페이지 수가 줄어 지금 페이지가 더 이상 없을 때 - 마지막 페이지로 보정합니다.
        if (data.totalPages > 0 && page > data.totalPages) {
          setPage(data.totalPages)
          return
        }
        setReviews(data.content)
        setTotalPages(data.totalPages)
        setTotalElements(data.totalElements)
        setStatus('ready')
      })
      .catch(() => {
        if (fetchIdRef.current !== requestId) return
        setStatus('error')
      })
  }

  const loadStats = () => {
    setStatsStatus('loading')
    getAdminReviewStats(user.id)
      .then((data) => {
        setStats(data)
        setStatsStatus('ready')
      })
      .catch(() => setStatsStatus('error'))
  }

  // 드롭다운 선택지 - 리뷰를 삭제하면(개별/일괄/신고 다이얼로그 경유 모두) 그 리뷰가 유일했던 영화
  // 제목·작성자 닉네임이 선택지에서도 빠져야 하므로, 삭제가 일어나는 모든 지점에서 다시 불러옵니다
  // (2026-11-21 실측 - 처음 마운트될 때 한 번만 불러왔더니 삭제 후에도 이미 없는 선택지가 계속 남아있었음).
  const loadFilterOptions = () => {
    const requestId = ++filterOptionsRequestRef.current
    getAdminReviewFilterOptions(user.id)
      .then((data) => {
        if (filterOptionsRequestRef.current !== requestId) return
        setFilterOptionPairs(data)
      })
      .catch(() => {
        if (filterOptionsRequestRef.current !== requestId) return
        setFilterOptionPairs([])
      })
  }

  useEffect(() => {
    loadReviews()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.id, page, reviewSort, filters])

  useEffect(() => {
    loadStats()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.id])

  useEffect(() => {
    loadFilterOptions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.id])

  // "전체 선택" 체크박스 상태 계산용 - 지금 필터에 맞는 리뷰 전체의 id. 서버가 한 페이지씩만 내려주게
  // 된 뒤로, 화면에 없는 다른 페이지의 선택 여부까지 판단하려면 이 목록이 따로 필요합니다.
  useEffect(() => {
    const requestId = ++matchingIdsRequestRef.current
    getAdminReviewIds(user.id, {
      movieTitle: filters.movieTitle,
      authorNickname: filters.authorNickname,
      reportedOnly: filters.reportedOnly,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      content: filters.content,
    })
      .then((ids) => {
        if (matchingIdsRequestRef.current !== requestId) return
        setMatchingIds(ids)
      })
      .catch(() => {
        if (matchingIdsRequestRef.current !== requestId) return
        setMatchingIds([])
      })
  }, [user.id, filters])

  // 필터/정렬/페이지/탭을 주소창 쿼리에 반영합니다 - 새로고침하거나 이 링크를 복사해서 보내도 지금
  // 보던 조건 그대로 다시 열립니다(2026-11-24 요청). 기본값과 같은 항목은 아예 쿼리에서 빼서 주소를
  // 짧게 유지하고, 브라우저 히스토리가 조작마다 쌓이지 않도록 replace로 갱신합니다. 반대 방향(주소창을
  // 직접 고쳤을 때 상태에 반영)은 지원하지 않습니다 - 새로고침/링크 공유라는 원래 목적에는 이 정도로
  // 충분하고, 뒤로/앞으로 가기까지 완전히 맞추려면 훨씬 복잡해집니다.
  useEffect(() => {
    const next = new URLSearchParams()
    if (activeTab !== 'reviews') next.set('tab', activeTab)
    if (page !== 1) next.set('page', String(page))
    if (reviewSort !== 'latest') next.set('sort', reviewSort)
    if (filters.movieTitle) next.set('movieTitle', filters.movieTitle)
    if (filters.authorNickname) next.set('authorNickname', filters.authorNickname)
    if (filters.reportedOnly) next.set('reportedOnly', 'true')
    if (filters.dateFrom) next.set('dateFrom', filters.dateFrom)
    if (filters.dateTo) next.set('dateTo', filters.dateTo)
    if (filters.content) next.set('content', filters.content)
    setSearchParams(next, { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, page, reviewSort, filters])

  // "삭제 이력" 탭을 열 때마다 다시 불러옵니다 - 예전에는 이 탭을 한 번 보고 나면 logsStatus가
  // 다시는 'idle'로 안 돌아가서, 그 뒤에 다른 탭에서 리뷰를 삭제하고 돌아와도 새 삭제 이력이
  // 안 보였습니다(2026-11-24 실측 - 새로고침해야만 보임).
  useEffect(() => {
    if (activeTab !== 'logs') return
    setLogsStatus('loading')
    getReviewDeletionLogs(user.id)
      .then((data) => {
        setDeletionLogs(data)
        setLogsStatus('ready')
      })
      .catch(() => setLogsStatus('error'))
  }, [activeTab, user.id])

  // 필터/정렬이 바뀌면 지금 보던 페이지 번호가 더 이상 안 맞을 수 있어 같은 이벤트 안에서 1페이지로
  // 함께 되돌립니다(따로 effect로 나누면, page가 아직 옛 값인 채로 새 필터를 한 번 더 조회하게 됩니다).
  const handleFiltersChange = (nextFilters) => {
    setFilters(nextFilters)
    setPage(1)
  }

  const handleSortChange = (nextSort) => {
    setReviewSort(nextSort)
    setPage(1)
  }

  const handleLogFiltersChange = (nextFilters) => {
    setLogFilters(nextFilters)
    setLogsPage(1)
  }

  const handleLogSortChange = (nextSort) => {
    setLogSort(nextSort)
    setLogsPage(1)
  }

  const handleDelete = (reviewId) => {
    setPendingDeleteReviewId(reviewId)
  }

  const confirmDelete = async () => {
    const reviewId = pendingDeleteReviewId
    setPendingDeleteReviewId(null)
    try {
      await deleteReviewAsAdmin(reviewId, user.id)
      setSelectedIds((prev) => {
        const next = new Set(prev)
        next.delete(reviewId)
        return next
      })
      setMatchingIds((prev) => prev.filter((id) => id !== reviewId))
      loadReviews()
      loadStats()
      loadFilterOptions()
    } catch (error) {
      setDeleteErrorMessage(error.message)
    }
  }

  const confirmBulkDelete = async () => {
    setPendingBulkDelete(false)
    const ids = Array.from(selectedIds)
    try {
      const failures = await bulkDeleteReviewsAsAdmin(ids, user.id)
      const failedIds = new Set(failures.map((failure) => Number(failure.split(':')[0])))
      const deletedIds = ids.filter((id) => !failedIds.has(id))
      setSelectedIds(new Set())
      setMatchingIds((prev) => prev.filter((id) => !deletedIds.includes(id)))
      loadReviews()
      loadStats()
      loadFilterOptions()
      if (failures.length > 0) {
        setDeleteErrorMessage(`일부 리뷰는 삭제하지 못했습니다:\n${failures.join('\n')}`)
      }
    } catch (error) {
      setDeleteErrorMessage(error.message)
    }
  }

  // ReviewReportsDialog에서 리뷰가 삭제되면 목록을 다시 불러옵니다. 다이얼로그는 여기서 닫지 않습니다 -
  // "신고 취소"처럼 다이얼로그 안에 이미 "리뷰를 삭제했습니다" 안내가 구현돼 있는데, 삭제되자마자
  // 바로 닫아버리면 그 안내가 뜰 틈도 없이 사라져서 관리자가 삭제 여부를 확인할 수 없었습니다
  // (2026-11-21 실측). 닫기는 다이얼로그의 ✕ 버튼을 직접 눌러야 합니다.
  const handleReviewDeletedFromDialog = (reviewId) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      next.delete(reviewId)
      return next
    })
    setMatchingIds((prev) => prev.filter((id) => id !== reviewId))
    setDetailReview((prev) => (prev?.id === reviewId ? null : prev))
    loadReviews()
    loadStats()
    loadFilterOptions()
  }

  // ReviewReportsDialog에서 신고 건을 기각하면 "신고된 것만" 필터에 걸쳐 있을 수 있어 목록을 다시
  // 불러옵니다. ReviewDetailDialog가 같은 리뷰를 보여주고 있다면 그쪽 숫자도 바로 맞춰둡니다.
  const handleReportCountChange = (reviewId, nextCount) => {
    setDetailReview((prev) => (prev?.id === reviewId ? { ...prev, reportCount: nextCount } : prev))
    loadReviews()
  }

  // ReviewDetailDialog의 "신고 N건 보기" - 다이얼로그 두 개가 겹치지 않도록, 상세 다이얼로그를 닫고
  // 신고 상세 다이얼로그를 엽니다.
  const handleViewReportsFromDetail = (reviewId) => {
    setDetailReview(null)
    setReportsDialogReviewId(reviewId)
  }

  // 드롭다운에 보여줄 선택지 - 실제로 리뷰가 존재하는 조합만, 가나다순으로 보여줍니다. 두 드롭다운은
  // 서로 연동됩니다 - 작성자를 먼저 고르면 영화 제목 쪽은 그 작성자가 실제로 쓴 영화만 남습니다.
  const movieTitleOptions = [
    ...new Set(
      filterOptionPairs
        .filter((pair) => !filters.authorNickname || pair.authorNickname === filters.authorNickname)
        .map((pair) => pair.movieTitle)
    ),
  ].sort((a, b) => a.localeCompare(b, 'ko'))
  const authorNicknameOptions = [
    ...new Set(
      filterOptionPairs
        .filter((pair) => !filters.movieTitle || pair.movieTitle === filters.movieTitle)
        .map((pair) => pair.authorNickname)
    ),
  ].sort((a, b) => a.localeCompare(b, 'ko'))

  const toggleSelect = (reviewId) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(reviewId)) next.delete(reviewId)
      else next.add(reviewId)
      return next
    })
  }

  // 헤더의 "전체 선택" 체크박스는 지금 페이지가 아니라 검색/필터 조건에 맞는 전체(matchingIds)를
  // 기준으로 합니다 - 그래야 "전체 선택" 후 다른 페이지로 넘어가도 그 페이지 항목들도 체크된 채로
  // 보이고, 삭제 버튼의 건수도 실제로 선택된 전체 건수와 맞습니다.
  const allMatchingSelected = matchingIds.length > 0 && matchingIds.every((id) => selectedIds.has(id))
  // 전체는 아니지만 일부라도 선택됐는지 - 헤더 체크박스를 "일부 선택" 모양(indeterminate)으로
  // 보여줄지 판단하는 데 씁니다(ReportTable 참고).
  const someMatchingSelected = matchingIds.some((id) => selectedIds.has(id))

  const toggleSelectAll = () => {
    setSelectedIds(allMatchingSelected ? new Set() : new Set(matchingIds))
  }

  // 드롭다운 선택지와 연동 방식은 리뷰 목록 탭과 동일합니다 - 작성자를 먼저 고르면 영화 제목 쪽은
  // 그 작성자가 쓴(삭제된) 리뷰의 영화만 남습니다.
  const logMovieTitleOptions = [
    ...new Set(
      deletionLogs
        .filter((log) => !logFilters.authorNickname || log.reviewAuthorNickname === logFilters.authorNickname)
        .map((log) => log.movieTitle)
    ),
  ].sort((a, b) => a.localeCompare(b, 'ko'))
  const logAuthorNicknameOptions = [
    ...new Set(
      deletionLogs
        .filter((log) => !logFilters.movieTitle || log.movieTitle === logFilters.movieTitle)
        .map((log) => log.reviewAuthorNickname)
    ),
  ].sort((a, b) => a.localeCompare(b, 'ko'))

  const filteredLogs = deletionLogs.filter((log) => matchesLogFilters(log, logFilters))

  // 최신순이 아니면(평점순/신고 사유 많은 순) 다시 정렬하고, 최신순은 서버가 이미 deletedAt desc로
  // 내려준 순서를 그대로 둡니다(리뷰 목록 탭의 정렬 방식과 동일).
  const sortedLogs = [...filteredLogs].sort((a, b) => {
    if (logSort === 'rating') return (b.reviewScore ?? -1) - (a.reviewScore ?? -1)
    if (logSort === 'reportReasonCount') {
      const countOf = (log) => (log.reportReasons ? log.reportReasons.split(', ').length : 0)
      return countOf(b) - countOf(a)
    }
    return 0
  })

  const logsTotalPages = Math.max(1, Math.ceil(sortedLogs.length / LOG_PAGE_SIZE))
  const currentLogsPage = Math.min(logsPage, logsTotalPages)
  const pageLogs = sortedLogs.slice((currentLogsPage - 1) * LOG_PAGE_SIZE, currentLogsPage * LOG_PAGE_SIZE)

  return (
    <div className="px-8 py-6">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-white">리뷰 관리</h1>
      </div>

      {statsStatus === 'ready' && (
        <div className="flex flex-col gap-4 sm:flex-row">
          <StatCard label="전체 리뷰" value={`${stats.totalCount}건`} />
          <StatCard label="오늘 작성된 리뷰" value={`${stats.todayCount}건`} />
        </div>
      )}

      <div className="mt-6 flex gap-2">
        <button
          type="button"
          onClick={() => setActiveTab('reviews')}
          className={`rounded-md px-3 py-1.5 text-sm font-semibold ${
            activeTab === 'reviews' ? 'bg-indigo-600 text-white' : 'bg-slate-900 text-gray-400 hover:text-gray-200'
          }`}
        >
          리뷰 목록
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('logs')}
          className={`rounded-md px-3 py-1.5 text-sm font-semibold ${
            activeTab === 'logs' ? 'bg-indigo-600 text-white' : 'bg-slate-900 text-gray-400 hover:text-gray-200'
          }`}
        >
          삭제 이력
        </button>
      </div>

      {activeTab === 'reviews' && (
        <div className="mt-4 rounded-xl bg-slate-900/60 p-4">
          {statsStatus === 'ready' && stats.totalCount > 0 && (
            <>
              <ReviewFilterBar
                filters={filters}
                onChange={handleFiltersChange}
                movieTitleOptions={movieTitleOptions}
                authorNicknameOptions={authorNicknameOptions}
              />
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <span className="text-sm text-gray-400">{totalElements}건</span>
                  {selectedIds.size > 0 && (
                    <>
                      <button
                        type="button"
                        onClick={() => setPendingBulkDelete(true)}
                        className="rounded-md bg-rose-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-rose-500"
                      >
                        선택한 {selectedIds.size}건 삭제
                      </button>
                      {/* 선택은 페이지를 넘나들어도 유지되므로, 지금 보이는 페이지에 없는 선택이 섞여
                          있을 때만 안내합니다 - 그렇지 않으면 "왜 아무것도 안 눌렀는데 N건이지?" 싶을 수 있습니다. */}
                      {Array.from(selectedIds).some((id) => !reviews.some((review) => review.id === id)) && (
                        <span className="text-xs text-gray-500">(다른 페이지 포함)</span>
                      )}
                    </>
                  )}
                </div>
                <SortDropdown value={reviewSort} options={REVIEW_SORT_OPTIONS} onChange={handleSortChange} />
              </div>
            </>
          )}
          {status === 'loading' && <p className="text-sm text-gray-400">리뷰를 불러오는 중...</p>}
          {status === 'error' && <p className="text-sm text-red-400">리뷰를 불러오지 못했습니다. 다시 시도해주세요.</p>}
          {statsStatus === 'ready' && stats.totalCount === 0 && (
            <p className="text-sm text-gray-400">작성된 리뷰가 없습니다.</p>
          )}
          {status === 'ready' && stats?.totalCount > 0 && totalElements === 0 && (
            <p className="text-sm text-gray-400">조건에 맞는 리뷰가 없습니다.</p>
          )}
          {status === 'ready' && reviews.length > 0 && (
            <>
              <ReportTable
                reviews={reviews}
                selectedIds={selectedIds}
                allSelected={allMatchingSelected}
                someSelected={someMatchingSelected}
                onToggleSelect={toggleSelect}
                onToggleSelectAll={toggleSelectAll}
                onDelete={handleDelete}
                onViewReports={setReportsDialogReviewId}
                onViewDetail={setDetailReview}
              />
              <Pagination page={page} totalPages={Math.max(1, totalPages)} onChange={setPage} />
            </>
          )}
        </div>
      )}

      {activeTab === 'logs' && (
        <div className="mt-4 rounded-xl bg-slate-900/60 p-4">
          {logsStatus === 'ready' && deletionLogs.length > 0 && (
            <>
              <DeletionLogFilterBar
                filters={logFilters}
                onChange={handleLogFiltersChange}
                movieTitleOptions={logMovieTitleOptions}
                authorNicknameOptions={logAuthorNicknameOptions}
              />
              <div className="mb-4 flex items-center justify-between">
                <span className="text-sm text-gray-400">{filteredLogs.length}건</span>
                <SortDropdown value={logSort} options={LOG_SORT_OPTIONS} onChange={handleLogSortChange} />
              </div>
            </>
          )}
          {logsStatus === 'loading' && <p className="text-sm text-gray-400">삭제 이력을 불러오는 중...</p>}
          {logsStatus === 'error' && <p className="text-sm text-red-400">삭제 이력을 불러오지 못했습니다.</p>}
          {logsStatus === 'ready' && deletionLogs.length === 0 && (
            <p className="text-sm text-gray-400">삭제된 리뷰가 없습니다.</p>
          )}
          {logsStatus === 'ready' && deletionLogs.length > 0 && filteredLogs.length === 0 && (
            <p className="text-sm text-gray-400">조건에 맞는 삭제 이력이 없습니다.</p>
          )}
          {logsStatus === 'ready' && pageLogs.length > 0 && (
            <>
              <DeletionLogTable logs={pageLogs} onViewDetail={setDetailLog} />
              <Pagination page={currentLogsPage} totalPages={logsTotalPages} onChange={setLogsPage} />
            </>
          )}
        </div>
      )}

      {detailReview != null && (
        <ReviewDetailDialog
          review={detailReview}
          onClose={() => setDetailReview(null)}
          onViewReports={handleViewReportsFromDetail}
        />
      )}

      {detailLog != null && <DeletionLogDetailDialog log={detailLog} onClose={() => setDetailLog(null)} />}

      {reportsDialogReviewId != null && (
        <ReviewReportsDialog
          adminUserId={user.id}
          reviewId={reportsDialogReviewId}
          onClose={() => setReportsDialogReviewId(null)}
          onReviewDeleted={handleReviewDeletedFromDialog}
          onReportCountChange={handleReportCountChange}
        />
      )}

      {pendingDeleteReviewId != null && (
        <ConfirmDialog
          title="리뷰 삭제"
          message="이 리뷰를 삭제하시겠습니까?"
          confirmLabel="삭제"
          danger
          onConfirm={confirmDelete}
          onCancel={() => setPendingDeleteReviewId(null)}
        />
      )}

      {pendingBulkDelete && (
        <ConfirmDialog
          title="리뷰 일괄 삭제"
          message={`선택한 ${selectedIds.size}건의 리뷰를 삭제하시겠습니까?`}
          confirmLabel="삭제"
          danger
          onConfirm={confirmBulkDelete}
          onCancel={() => setPendingBulkDelete(false)}
        />
      )}

      {deleteErrorMessage && (
        <InfoDialog title="리뷰 삭제 실패" message={deleteErrorMessage} tone="error" onClose={() => setDeleteErrorMessage('')} />
      )}
    </div>
  )
}

export default AdminReportsPage
