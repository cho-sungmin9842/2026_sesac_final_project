import request from './httpClient'

// 백엔드(application.yml 의 app.version)가 가진 앱 버전을 가져옵니다. 응답 예: { "version": "V.1.0" }
export async function getAppVersion() {
  const { version } = await request('/api/app-version')
  return version
}
