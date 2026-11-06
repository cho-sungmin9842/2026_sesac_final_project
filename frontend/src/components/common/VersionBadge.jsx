import { useEffect, useState } from 'react'
import { getAppVersion } from '../../api/appInfoApi'

// 앱 버전은 프론트에 적어두지 않고 백엔드(GET /api/app-version, application.yml 의 app.version)에서 가져옵니다.
// 버전을 올릴 때는 백엔드 설정 한 곳만 고치면 사용자 화면(헤더)과 관리자 화면(사이드바)에 모두 같은 값이 반영됩니다.
// 헤더와 사이드바가 각각 이 컴포넌트를 쓰더라도 요청은 한 번만 보내도록, 진행 중/완료된 요청을 모듈에 보관해 함께 씁니다.
let versionRequest = null

function loadVersion() {
  if (!versionRequest) {
    versionRequest = getAppVersion().catch((error) => {
      // 실패한 결과를 붙잡아 두면 이후에도 계속 실패하므로, 다음에 다시 시도할 수 있게 비웁니다.
      versionRequest = null
      throw error
    })
  }
  return versionRequest
}

// 버전 표시(알약 모양). 글자는 가운데 정렬이라, 놓는 위치에서 className 으로 폭을 주면(예: block w-full) 그 폭을 채웁니다.
// 글자색 빨강(text-red-500), 굵기 650(font-[650]). 650 은 가변 굵기를 지원하는 글꼴에서만 650 그대로 보이고,
// 그렇지 않은 글꼴(윈도우 기본 글꼴 등)은 가장 가까운 굵은 글꼴(700)로 그려집니다.
// 버전을 아직 못 받았거나(로딩) 받지 못했을 때(서버 오류)는 자리만 잡고 보이지 않게 해서, 값이 도착해도 화면이 흔들리지 않습니다.
function VersionBadge({ className = '' }) {
  const [version, setVersion] = useState(null)

  useEffect(() => {
    let cancelled = false
    loadVersion()
      .then((value) => {
        if (!cancelled) setVersion(value)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <span
      title={version ? `MoviePick ${version}` : undefined}
      aria-label={version ? `앱 버전 ${version}` : undefined}
      aria-hidden={version ? undefined : true}
      className={`select-none rounded-full border border-white/10 px-2 py-0.5 text-center text-[20px] font-[650] tracking-wide text-red-500 ${
        version ? '' : 'invisible'
      } ${className}`}
    >
      {version ?? 'V.0.0'}
    </span>
  )
}

export default VersionBadge
