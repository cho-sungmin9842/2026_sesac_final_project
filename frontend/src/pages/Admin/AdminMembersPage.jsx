import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { getAllUsersForAdmin } from '../../api/userApi'
import MemberTable from './components/MemberTable'
import StatCard from './components/StatCard'

function AdminMembersPage() {
  const { user } = useAuth()
  const [members, setMembers] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error

  useEffect(() => {
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
  }, [user.id])

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
                <MemberTable members={regularMembers} />
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}

export default AdminMembersPage
