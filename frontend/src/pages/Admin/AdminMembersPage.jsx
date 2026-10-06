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

  const adminCount = members.filter((member) => member.isAdmin).length

  return (
    <div className="px-8 py-6">
      <h1 className="text-xl font-bold text-white">회원 관리</h1>
      <p className="mt-1 text-sm text-gray-400">가입한 전체 회원의 정보와 활동 현황을 볼 수 있습니다.</p>

      <div className="mt-6 flex gap-4">
        <StatCard label="전체 회원" value={`${members.length}명`} />
        <StatCard label="관리자" value={`${adminCount}명`} />
        <StatCard label="일반회원" value={`${members.length - adminCount}명`} />
      </div>

      <div className="mt-6 rounded-xl bg-slate-900/60 p-4">
        {status === 'loading' && <p className="text-sm text-gray-400">회원 정보를 불러오는 중...</p>}
        {status === 'error' && <p className="text-sm text-red-400">회원 정보를 불러오지 못했습니다. 다시 시도해주세요.</p>}
        {status === 'ready' && members.length === 0 && (
          <p className="text-sm text-gray-400">가입한 회원이 없습니다.</p>
        )}
        {status === 'ready' && members.length > 0 && <MemberTable members={members} />}
      </div>
    </div>
  )
}

export default AdminMembersPage
