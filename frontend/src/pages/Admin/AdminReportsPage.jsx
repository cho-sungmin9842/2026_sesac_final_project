import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { deleteReviewAsAdmin, getAllReviewsForAdmin } from '../../api/reviewApi'
import ReportTable from './components/ReportTable'
import StatCard from './components/StatCard'
import ConfirmDialog from '../../components/common/ConfirmDialog'
import InfoDialog from '../../components/common/InfoDialog'

function isToday(dateTimeStr) {
  const date = new Date(dateTimeStr)
  const now = new Date()
  return (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  )
}

// "신고(report)" 자체는 아직 별도로 수집하지 않아(신고 사유/상태를 저장하는 테이블이 없음), 이 화면은
// DB에 저장된 모든 사용자의 리뷰를 그대로 보여주고, 관리자가 부적절한 리뷰를 바로 삭제할 수 있게 합니다.
function AdminReportsPage() {
  const { user } = useAuth()
  const [reviews, setReviews] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [pendingDeleteReviewId, setPendingDeleteReviewId] = useState(null)
  const [deleteErrorMessage, setDeleteErrorMessage] = useState('')

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

  const handleDelete = (reviewId) => {
    setPendingDeleteReviewId(reviewId)
  }

  const confirmDelete = async () => {
    const reviewId = pendingDeleteReviewId
    setPendingDeleteReviewId(null)
    try {
      await deleteReviewAsAdmin(reviewId, user.id)
      setReviews((prev) => prev.filter((review) => review.id !== reviewId))
    } catch (error) {
      setDeleteErrorMessage(error.message)
    }
  }

  const todayCount = reviews.filter((review) => isToday(review.createdAt)).length

  return (
    <div className="px-8 py-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-bold text-white">리뷰 관리</h1>
        <span className="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-gray-300">
          오늘 작성 {todayCount}건
        </span>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row">
        <StatCard label="전체 리뷰" value={`${reviews.length}건`} />
        <StatCard label="오늘 작성된 리뷰" value={`${todayCount}건`} />
      </div>

      <div className="mt-6 rounded-xl bg-slate-900/60 p-4">
        {status === 'loading' && <p className="text-sm text-gray-400">리뷰를 불러오는 중...</p>}
        {status === 'error' && <p className="text-sm text-red-400">리뷰를 불러오지 못했습니다. 다시 시도해주세요.</p>}
        {status === 'ready' && reviews.length === 0 && (
          <p className="text-sm text-gray-400">작성된 리뷰가 없습니다.</p>
        )}
        {status === 'ready' && reviews.length > 0 && <ReportTable reviews={reviews} onDelete={handleDelete} />}
      </div>

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

      {deleteErrorMessage && (
        <InfoDialog title="리뷰 삭제 실패" message={deleteErrorMessage} tone="error" onClose={() => setDeleteErrorMessage('')} />
      )}
    </div>
  )
}

export default AdminReportsPage
