import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { getAllUsersForAdmin, suspendUser, deleteUserForAdmin } from '../../api/userApi'
import MemberTable from './components/MemberTable'
import StatCard from './components/StatCard'
import SuspendReasonDialog from './components/SuspendReasonDialog'
import ConfirmDialog from '../../components/common/ConfirmDialog'
import InfoDialog from '../../components/common/InfoDialog'

function AdminMembersPage() {
  const { user } = useAuth()
  const [members, setMembers] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [pendingSuspendMember, setPendingSuspendMember] = useState(null)
  const [pendingDeleteMember, setPendingDeleteMember] = useState(null)
  const [actionErrorMessage, setActionErrorMessage] = useState('')

  const loadMembers = () => {
    let cancelled = false
    getAllUsersForAdmin(user.id)
      .then((data) => {
        if (cancelled) return
        setMembers(data)
        setStatus('ready')
      })
      .catch(() => {
        if (cancelled) return
        setStatus('error')
      })
    return () => {
      cancelled = true
    }
  }

  useEffect(loadMembers, [user.id])

  // 온라인/오프라인 표시가 실시간처럼 느껴지도록, NotificationBell의 알림 폴링과 같은 주기로
  // 다시 불러옵니다(수동 새로고침 없이도 방금 접속/이탈한 회원이 반영됩니다).
  useEffect(() => {
    const interval = setInterval(loadMembers, 20000)
    return () => clearInterval(interval)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.id])

  // 정지시킬 때만 사유를 먼저 고르게 합니다(SuspendReasonDialog) - 해제는 사유가 필요 없어 바로 호출합니다.
  const handleToggleSuspend = (member) => {
    if (member.isSuspended) {
      suspendUser(user.id, member.id, false)
        .then(loadMembers)
        .catch((error) => setActionErrorMessage(error.message))
      return
    }
    setPendingSuspendMember(member)
  }

  const confirmSuspend = (reason) => {
    const member = pendingSuspendMember
    setPendingSuspendMember(null)
    suspendUser(user.id, member.id, true, reason)
      .then(loadMembers)
      .catch((error) => setActionErrorMessage(error.message))
  }

  const confirmDelete = () => {
    const member = pendingDeleteMember
    setPendingDeleteMember(null)
    deleteUserForAdmin(user.id, member.id)
      .then(loadMembers)
      .catch((error) => setActionErrorMessage(error.message))
  }

  const adminMembers = members.filter((member) => member.isAdmin)
  const regularMembers = members.filter((member) => !member.isAdmin)
  const adminCount = adminMembers.length

  return (
    <div className="px-8 py-6">
      <h1 className="text-xl font-bold text-white">회원 관리</h1>
      <p className="mt-1 text-sm text-gray-400">가입한 전체 회원의 정보와 활동 현황을 볼 수 있습니다.</p>

      <div className="mt-6 flex flex-col gap-4 sm:flex-row">
        <StatCard label="전체 회원" value={`${members.length}명`} />
        <StatCard label="관리자" value={`${adminCount}명`} />
        <StatCard label="일반회원" value={`${members.length - adminCount}명`} />
      </div>

      {status === 'loading' && (
        <div className="mt-6 rounded-xl bg-slate-900/60 p-4">
          <p className="text-sm text-gray-400">회원 정보를 불러오는 중...</p>
        </div>
      )}
      {status === 'error' && (
        <div className="mt-6 rounded-xl bg-slate-900/60 p-4">
          <p className="text-sm text-red-400">회원 정보를 불러오지 못했습니다. 다시 시도해주세요.</p>
        </div>
      )}
      {status === 'ready' && members.length === 0 && (
        <div className="mt-6 rounded-xl bg-slate-900/60 p-4">
          <p className="text-sm text-gray-400">가입한 회원이 없습니다.</p>
        </div>
      )}

      {status === 'ready' && members.length > 0 && (
        <>
          <div className="mt-6">
            <h2 className="mb-2 text-sm font-semibold text-gray-300">관리자</h2>
            <div className="rounded-xl bg-slate-900/60 p-4">
              {adminMembers.length === 0 ? (
                <p className="text-sm text-gray-400">관리자 계정이 없습니다.</p>
              ) : (
                <MemberTable members={adminMembers} />
              )}
            </div>
          </div>

          <div className="mt-6">
            <h2 className="mb-2 text-sm font-semibold text-gray-300">일반회원</h2>
            <div className="rounded-xl bg-slate-900/60 p-4">
              {regularMembers.length === 0 ? (
                <p className="text-sm text-gray-400">가입한 일반회원이 없습니다.</p>
              ) : (
                <MemberTable
                  members={regularMembers}
                  onToggleSuspend={handleToggleSuspend}
                  onDelete={(member) => setPendingDeleteMember(member)}
                />
              )}
            </div>
          </div>
        </>
      )}

      {pendingSuspendMember && (
        <SuspendReasonDialog
          member={pendingSuspendMember}
          onConfirm={confirmSuspend}
          onCancel={() => setPendingSuspendMember(null)}
        />
      )}

      {pendingDeleteMember && (
        <ConfirmDialog
          title="회원 삭제"
          message={`"${pendingDeleteMember.nickname}"(${pendingDeleteMember.username}) 계정을 삭제할까요?\n리뷰·예매·찜 등 관련 데이터가 모두 함께 삭제되며, 되돌릴 수 없습니다.`}
          confirmLabel="삭제"
          danger
          onConfirm={confirmDelete}
          onCancel={() => setPendingDeleteMember(null)}
        />
      )}

      {actionErrorMessage && (
        <InfoDialog
          title="처리 실패"
          message={actionErrorMessage}
          tone="error"
          onClose={() => setActionErrorMessage('')}
        />
      )}
    </div>
  )
}

export default AdminMembersPage
