import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import {
  bulkDeleteReviewsAsAdmin,
  deleteReviewAsAdmin,
  getAllReviewsForAdmin,
  getReviewDeletionLogs,
} from '../../api/reviewApi'
import ReportTable from './components/ReportTable'
import ReviewDetailDialog from './components/ReviewDetailDialog'
import ReviewReportsDialog from './components/ReviewReportsDialog'
import ReviewFilterBar, { EMPTY_REVIEW_FILTERS } from './components/ReviewFilterBar'
import DeletionLogTable from './components/DeletionLogTable'
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

const PAGE_SIZE = 10

function isToday(dateTimeStr) {
  const date = new Date(dateTimeStr)
  const now = new Date()
  return (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  )
}

// movieTitle/authorNickname은 이제 직접 타이핑이 아니라 드롭다운에서 고르는 값이라, 부분 일치가
// 아니라 정확히 같은 값인지만 봅니다.
function matchesFilters(review, filters) {
  if (filters.movieTitle && review.movieTitle !== filters.movieTitle) return false
  if (filters.authorNickname && review.authorNickname !== filters.authorNickname) return false
  if (filters.reportedOnly && !(review.reportCount > 0)) return false
  // createdAt은 "2026-11-20T15:03:19" 형태라, 날짜 부분만 잘라 문자열로 비교해도 안전합니다.
  const createdDate = review.createdAt.slice(0, 10)
  if (filters.dateFrom && createdDate < filters.dateFrom) return false
  if (filters.dateTo && createdDate > filters.dateTo) return false
  return true
}

// DB에 저장된 모든 사용자의 리뷰를 보여주고, 관리자가 부적절한 리뷰를 바로(또는 여러 건 한 번에)
// 삭제할 수 있게 합니다. 신고된 리뷰는 reportCount로 표시되고, "신고 N건" 뱃지를 누르면
// ReviewReportsDialog에서 신고 사유/신고자까지 보고 그 자리에서 삭제·삭제취소를 처리할 수 있습니다.
// 검색/정렬/페이지네이션은 전부 이미 불러온 reviews 배열을 클라이언트에서 가공한 것입니다 - 지금
// 규모에선 충분하고, 리뷰가 훨씬 많아지면 서버 쪽 페이지네이션으로 옮기는 게 맞습니다.
function AdminReportsPage() {
  const { user } = useAuth()
  const [reviews, setReviews] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [pendingDeleteReviewId, setPendingDeleteReviewId] = useState(null)
  const [pendingBulkDelete, setPendingBulkDelete] = useState(false)
  const [deleteErrorMessage, setDeleteErrorMessage] = useState('')
  const [reviewSort, setReviewSort] = useState('latest')
  const [filters, setFilters] = useState(EMPTY_REVIEW_FILTERS)
  const [page, setPage] = useState(1)
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [reportsDialogReviewId, setReportsDialogReviewId] = useState(null)
  const [detailReview, setDetailReview] = useState(null)
  const [activeTab, setActiveTab] = useState('reviews') // 'reviews' | 'logs'
  const [deletionLogs, setDeletionLogs] = useState([])
  const [logsStatus, setLogsStatus] = useState('idle') // idle | loading | ready | error

  useEffect(() => {
    let cancelled = false
    getAllReviewsForAdmin(user.id)
      .then((data) => {
        if (cancelled) return
        setReviews(data)
        setStatus('ready')
      })
      .catch(() => {
        if (cancelled) return
        setStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [user.id])

  useEffect(() => {
    if (activeTab !== 'logs' || logsStatus !== 'idle') return
    setLogsStatus('loading')
    getReviewDeletionLogs(user.id)
      .then((data) => {
        setDeletionLogs(data)
        setLogsStatus('ready')
      })
      .catch(() => setLogsStatus('error'))
  }, [activeTab, logsStatus, user.id])

  // 필터/정렬이 바뀌면 지금 보던 페이지 번호가 더 이상 안 맞을 수 있어 1페이지로 되돌립니다.
  useEffect(() => {
    setPage(1)
  }, [filters, reviewSort])

  const handleDelete = (reviewId) => {
    setPendingDeleteReviewId(reviewId)
  }

  const confirmDelete = async () => {
    const reviewId = pendingDeleteReviewId
    setPendingDeleteReviewId(null)
    try {
      await deleteReviewAsAdmin(reviewId, user.id)
      setReviews((prev) => prev.filter((review) => review.id !== reviewId))
      setSelectedIds((prev) => {
        const next = new Set(prev)
        next.delete(reviewId)
        return next
      })
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
      setReviews((prev) => prev.filter((review) => !ids.includes(review.id) || failedIds.has(review.id)))
      setSelectedIds(new Set())
      if (failures.length > 0) {
        setDeleteErrorMessage(`일부 리뷰는 삭제하지 못했습니다:\n${failures.join('\n')}`)
      }
    } catch (error) {
      setDeleteErrorMessage(error.message)
    }
  }

  // ReviewReportsDialog에서 리뷰가 삭제되면 목록에서도 바로 빼고 다이얼로그를 닫습니다.
  const handleReviewDeletedFromDialog = (reviewId) => {
    setReviews((prev) => prev.filter((review) => review.id !== reviewId))
    setReportsDialogReviewId(null)
    setDetailReview((prev) => (prev?.id === reviewId ? null : prev))
  }

  // ReviewReportsDialog에서 신고 건을 기각하면, 다시 전체 목록을 불러오지 않고 그 리뷰의 뱃지
  // 숫자만 바로 갱신합니다. ReviewDetailDialog가 같은 리뷰를 보여주고 있다면 그쪽 숫자도 맞춥니다.
  const handleReportCountChange = (reviewId, nextCount) => {
    setReviews((prev) =>
      prev.map((review) => (review.id === reviewId ? { ...review, reportCount: nextCount } : review))
    )
    setDetailReview((prev) => (prev?.id === reviewId ? { ...prev, reportCount: nextCount } : prev))
  }

  // ReviewDetailDialog의 "신고 N건 보기" - 다이얼로그 두 개가 겹치지 않도록, 상세 다이얼로그를 닫고
  // 신고 상세 다이얼로그를 엽니다.
  const handleViewReportsFromDetail = (reviewId) => {
    setDetailReview(null)
    setReportsDialogReviewId(reviewId)
  }

  const todayCount = reviews.filter((review) => isToday(review.createdAt)).length

  // 드롭다운에 보여줄 선택지 - 지금 불러온 리뷰들에 실제로 등장하는 영화 제목/작성자 닉네임만
  // 중복 없이, 가나다순으로 보여줍니다.
  const movieTitleOptions = [...new Set(reviews.map((review) => review.movieTitle))].sort((a, b) =>
    a.localeCompare(b, 'ko')
  )
  const authorNicknameOptions = [...new Set(reviews.map((review) => review.authorNickname))].sort((a, b) =>
    a.localeCompare(b, 'ko')
  )

  const filteredReviews = reviews.filter((review) => matchesFilters(review, filters))

  // 평점순/신고 많은 순이 아니면(최신순) 서버가 이미 내려준 순서를 그대로 둡니다.
  const sortedReviews = [...filteredReviews].sort((a, b) => {
    if (reviewSort === 'rating') return (b.score ?? -1) - (a.score ?? -1)
    if (reviewSort === 'reportCount') return (b.reportCount ?? 0) - (a.reportCount ?? 0)
    return 0
  })

  const totalPages = Math.max(1, Math.ceil(sortedReviews.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pageReviews = sortedReviews.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  const toggleSelect = (reviewId) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(reviewId)) next.delete(reviewId)
      else next.add(reviewId)
      return next
    })
  }

  // 헤더의 "전체 선택" 체크박스는 지금 페이지가 아니라 검색/필터 조건에 맞는 전체(sortedReviews)를
  // 기준으로 합니다 - 그래야 "전체 선택" 후 다른 페이지로 넘어가도 그 페이지 항목들도 체크된 채로
  // 보이고, 삭제 버튼의 건수도 실제로 선택된 전체 건수와 맞습니다.
  const allFilteredSelected = sortedReviews.length > 0 && sortedReviews.every((review) => selectedIds.has(review.id))

  const toggleSelectAll = () => {
    setSelectedIds(allFilteredSelected ? new Set() : new Set(sortedReviews.map((review) => review.id)))
  }

  return (
    <div className="px-8 py-6">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-white">리뷰 관리</h1>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row">
        <StatCard label="전체 리뷰" value={`${reviews.length}건`} />
        <StatCard label="오늘 작성된 리뷰" value={`${todayCount}건`} />
      </div>

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
          {status === 'ready' && reviews.length > 0 && (
            <>
              <ReviewFilterBar
                filters={filters}
                onChange={setFilters}
                movieTitleOptions={movieTitleOptions}
                authorNicknameOptions={authorNicknameOptions}
              />
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <span className="text-sm text-gray-400">{sortedReviews.length}건</span>
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
                      {Array.from(selectedIds).some(
                        (id) => !pageReviews.some((review) => review.id === id)
                      ) && <span className="text-xs text-gray-500">(다른 페이지 포함)</span>}
                    </>
                  )}
                </div>
                <SortDropdown value={reviewSort} options={REVIEW_SORT_OPTIONS} onChange={setReviewSort} />
              </div>
            </>
          )}
          {status === 'loading' && <p className="text-sm text-gray-400">리뷰를 불러오는 중...</p>}
          {status === 'error' && <p className="text-sm text-red-400">리뷰를 불러오지 못했습니다. 다시 시도해주세요.</p>}
          {status === 'ready' && reviews.length === 0 && (
            <p className="text-sm text-gray-400">작성된 리뷰가 없습니다.</p>
          )}
          {status === 'ready' && reviews.length > 0 && sortedReviews.length === 0 && (
            <p className="text-sm text-gray-400">조건에 맞는 리뷰가 없습니다.</p>
          )}
          {status === 'ready' && pageReviews.length > 0 && (
            <>
              <ReportTable
                reviews={pageReviews}
                selectedIds={selectedIds}
                allSelected={allFilteredSelected}
                onToggleSelect={toggleSelect}
                onToggleSelectAll={toggleSelectAll}
                onDelete={handleDelete}
                onViewReports={setReportsDialogReviewId}
                onViewDetail={setDetailReview}
              />
              <Pagination page={currentPage} totalPages={totalPages} onChange={setPage} />
            </>
          )}
        </div>
      )}

      {activeTab === 'logs' && (
        <div className="mt-4 rounded-xl bg-slate-900/60 p-4">
          {logsStatus === 'loading' && <p className="text-sm text-gray-400">삭제 이력을 불러오는 중...</p>}
          {logsStatus === 'error' && <p className="text-sm text-red-400">삭제 이력을 불러오지 못했습니다.</p>}
          {logsStatus === 'ready' && deletionLogs.length === 0 && (
            <p className="text-sm text-gray-400">삭제된 리뷰가 없습니다.</p>
          )}
          {logsStatus === 'ready' && deletionLogs.length > 0 && <DeletionLogTable logs={deletionLogs} />}
        </div>
      )}

      {detailReview != null && (
        <ReviewDetailDialog
          review={detailReview}
          onClose={() => setDetailReview(null)}
          onViewReports={handleViewReportsFromDetail}
        />
      )}

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
