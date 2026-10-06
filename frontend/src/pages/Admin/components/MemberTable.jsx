function formatDate(dateTimeStr) {
  const date = new Date(dateTimeStr)
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

// 전체 회원 목록 - 비밀번호는 BCrypt로 해시돼 있어 애초에 복호화가 불가능하므로 보여줄 수 없고,
// 보여주지도 않습니다(아이디/닉네임/가입일/구분과 활동 집계만 표시).
function MemberTable({ members }) {
  return (
    <table className="w-full text-left text-sm">
      <thead>
        <tr className="border-b border-white/5 text-gray-400">
          <th className="py-3 font-medium">아이디</th>
          <th className="py-3 font-medium">닉네임</th>
          <th className="py-3 font-medium">구분</th>
          <th className="py-3 font-medium">가입일</th>
          <th className="py-3 font-medium">작성 리뷰</th>
          <th className="py-3 font-medium">예매 건수</th>
          <th className="py-3 font-medium">찜한 영화</th>
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
            <td className="py-4 whitespace-nowrap text-gray-400">{formatDate(member.createdAt)}</td>
            <td className="py-4 text-gray-300">{member.reviewCount}건</td>
            <td className="py-4 text-gray-300">{member.bookingCount}건</td>
            <td className="py-4 text-gray-300">{member.wishlistCount}편</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default MemberTable
