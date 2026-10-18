function ProfileHeader({ user, onChangePassword, onDeleteAccount }) {
  return (
    <div className="flex flex-col gap-4 border-b border-white/5 pb-6 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-4">
        <span className="block h-16 w-16 shrink-0 rounded-full bg-gradient-to-br from-orange-400 to-red-500" />
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-lg font-bold text-white">{user.nickname} 님</h1>
            <button
              type="button"
              onClick={onChangePassword}
              className="rounded-md bg-slate-800 px-2 py-1 text-xs font-semibold text-gray-300 hover:bg-slate-700"
            >
              비밀번호 변경
            </button>
          </div>
          <p className="text-sm text-gray-400">
            가입일 {user.joinedAt}
            <button type="button" onClick={onDeleteAccount} className="ml-3 text-xs text-gray-600 hover:text-rose-400 hover:underline">
              회원 탈퇴
            </button>
          </p>
        </div>
      </div>

      <div className="flex gap-4 text-center sm:gap-8">
        <div>
          <p className="text-xl font-bold text-white">{user.bookingCount}</p>
          <p className="text-xs text-gray-400">예매</p>
        </div>
        <div>
          <p className="text-xl font-bold text-white">{user.reviewCount}</p>
          <p className="text-xs text-gray-400">리뷰</p>
        </div>
        <div>
          <p className="text-xl font-bold text-white">{user.wishlistCount}</p>
          <p className="text-xs text-gray-400">찜한 영화</p>
        </div>
        <div>
          <p className="text-xl font-bold text-white">{user.watchedCount}</p>
          <p className="text-xs text-gray-400">시청완료</p>
        </div>
      </div>
    </div>
  )
}

export default ProfileHeader
