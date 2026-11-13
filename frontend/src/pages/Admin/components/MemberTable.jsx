import HorizontalScroll from '../../../components/common/HorizontalScroll'

function formatDate(dateTimeStr) {
  const date = new Date(dateTimeStr)
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

// 전체 회원 목록 - 비밀번호는 BCrypt로 해시돼 있어 애초에 복호화가 불가능하므로 보여줄 수 없고,
// 보여주지도 않습니다(아이디/닉네임/가입일/구분과 활동 집계만 표시).
// 컬럼이 많아 좁은 화면(모바일)에서는 표가 찌그러지므로, 가로 스크롤로 보게끔 감쌉니다.
// onToggleSuspend/onDelete는 일반회원 행에서만 쓰입니다(관리자는 정지/삭제 대상이 아니라 버튼 자체가
// 없습니다 - 백엔드도 관리자 계정을 대상으로 하면 거부합니다).
function MemberTable({ members, onToggleSuspend, onDelete }) {
  return (
    <HorizontalScroll>
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead>
          <tr className="border-b border-white/5 text-gray-400">
            <th className="py-3 font-medium">아이디</th>
            <th className="py-3 font-medium">닉네임</th>
            <th className="py-3 font-medium">구분</th>
            <th className="py-3 font-medium">접속</th>
            <th className="py-3 font-medium">상태</th>
            <th className="py-3 font-medium">가입일</th>
            <th className="py-3 font-medium">작성 리뷰</th>
            <th className="py-3 font-medium">예매 건수</th>
            <th className="py-3 font-medium">찜한 영화</th>
            {(onToggleSuspend || onDelete) && <th className="py-3 font-medium">관리</th>}
          </tr>
        </thead>
        <tbody>
          {members.map((member) => (
            <tr key={member.id} className="border-b border-white/5 text-gray-200">
              <td className="py-4 font-semibold">{member.username}</td>
              <td className="py-4 text-gray-300">{member.nickname}</td>
              <td className="py-4">
                {member.isAdmin ? (
                  <span className="rounded-full bg-indigo-600/20 px-2 py-0.5 text-xs font-semibold text-indigo-300">
                    관리자
                  </span>
                ) : (
                  <span className="rounded-full bg-slate-800 px-2 py-0.5 text-xs font-semibold text-gray-400">
                    일반회원
                  </span>
                )}
              </td>
              <td className="py-4">
                {member.isOnline ? (
                  <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-400">
                    <span className="h-2 w-2 rounded-full bg-emerald-400" />
                    온라인
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500">
                    <span className="h-2 w-2 rounded-full bg-gray-600" />
                    오프라인
                  </span>
                )}
              </td>
              <td className="py-4">
                {member.isSuspended ? (
                  <span
                    title={member.suspensionReasonLabel ?? undefined}
                    className="cursor-help rounded-full bg-rose-600/20 px-2 py-0.5 text-xs font-semibold text-rose-400"
                  >
                    정지됨
                  </span>
                ) : (
                  <span className="rounded-full bg-emerald-600/20 px-2 py-0.5 text-xs font-semibold text-emerald-400">
                    정상
                  </span>
                )}
              </td>
              <td className="py-4 whitespace-nowrap text-gray-400">{formatDate(member.createdAt)}</td>
              {/* 관리자는 리뷰 작성/예매/찜을 하지 않는 계정이라, 활동 집계 대신 "-"로 표시합니다. */}
              <td className="py-4 text-gray-300">{member.isAdmin ? '-' : `${member.reviewCount}건`}</td>
              <td className="py-4 text-gray-300">{member.isAdmin ? '-' : `${member.bookingCount}건`}</td>
              <td className="py-4 text-gray-300">{member.isAdmin ? '-' : `${member.wishlistCount}편`}</td>
              {(onToggleSuspend || onDelete) && (
                <td className="py-4 whitespace-nowrap">
                  {!member.isAdmin && (
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => onToggleSuspend(member)}
                        className="rounded-md border border-amber-500/50 px-2 py-1 text-xs font-semibold text-amber-400 hover:bg-amber-500/10"
                      >
                        {member.isSuspended ? '정지 해제' : '정지'}
                      </button>
                      <button
                        type="button"
                        onClick={() => onDelete(member)}
                        className="rounded-md border border-rose-500/50 px-2 py-1 text-xs font-semibold text-rose-400 hover:bg-rose-500/10"
                      >
                        삭제
                      </button>
                    </div>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </HorizontalScroll>
  )
}

export default MemberTable
