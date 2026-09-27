// 백엔드(KMDB 연동) 응답을 화면에서 쓰기 편한 모양으로 정리합니다.
import { pickOtherVideos, pickVideo } from '../../utils/pickVideo'

export function normalizeDetail(dto) {
  const video = pickVideo(dto.trailers)

  return {
    title: dto.title,
    year: dto.year,
    genres: dto.genres ?? [],
    runtimeMinutes: dto.runtimeMinutes,
    ageRating: dto.ageRating,
    directorsLabel: dto.directors?.length ? dto.directors.join(', ') : '정보 없음',
    castLabel: dto.actors?.length ? dto.actors.slice(0, 6).map((actor) => actor.name).join(', ') : '정보 없음',
    tags: [...new Set(dto.keywords ?? [])],
    plot: dto.plot ?? '줄거리 정보가 없습니다.',
    posterUrl: dto.posterUrl ?? null,
    posterUrls: dto.posterUrls ?? [],
    stillUrls: dto.stillUrls ?? [],
    video,
    otherVideos: pickOtherVideos(dto.trailers, video),
  }
}
