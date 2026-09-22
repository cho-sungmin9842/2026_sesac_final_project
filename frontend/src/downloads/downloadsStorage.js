// 다운로드 백엔드가 아직 없어서, 다운로드한 영화 목록을 브라우저(localStorage)에만 저장합니다.
const STORAGE_KEY = 'moviepick_downloads'

function readAll() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) ?? []
  } catch {
    return []
  }
}

function writeAll(list) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
}

export function getDownloads() {
  return readAll()
}

export function isDownloaded(movieId) {
  return readAll().some((item) => item.id === movieId)
}

export function addDownload(movie) {
  const all = readAll()
  if (all.some((item) => item.id === movie.id)) return
  all.unshift({ ...movie, downloadedAt: new Date().toISOString() })
  writeAll(all)
}

export function removeDownload(movieId) {
  writeAll(readAll().filter((item) => item.id !== movieId))
}
