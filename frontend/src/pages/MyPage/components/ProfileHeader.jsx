function ProfileHeader({ user }) {
  return (
    <div className="flex items-center justify-between border-b border-white/5 pb-6">
      <div className="flex items-center gap-4">
        <span className="block h-16 w-16 rounded-full bg-gradient-to-br from-orange-400 to-red-500" />
        <div>
          <h1 className="text-lg font-bold text-white">{user.nickname} 님</h1>
          <p className="text-sm text-gray-400">
            가입일 {user.joinedAt} · 등급 {user.tier}
          </p>
        </div>
      </div>

      <div className="flex gap-8 text-center">
        <div>
          <p className="text-xl font-bold text-white">{user.reviewCount}</p>
          <p className="text-xs text-gray-400">평점 남김</p>
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
