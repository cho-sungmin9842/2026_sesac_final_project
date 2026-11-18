# 🎬 SESAC MOVIE — Backend

Spring Boot 3 + MySQL 기반 REST API 서버입니다. [KMDB(한국영화데이터베이스) Open API](https://www.kmdb.or.kr/info/api/apiDetail/6)를 캐싱·재가공해 영화 정보를 제공하고, 좌석 예매·결제·리뷰·찜 목록·AI 챗봇까지 영화 예매 서비스에 필요한 기능 전체를 담당합니다.

루트 저장소의 전체 소개, 설치형 설정(DB·API 키)은 [최상위 README](../README.md)를 참고하세요. 이 문서는 백엔드 코드를 작업할 때 필요한 내용만 다룹니다.

## 🛠 기술 스택

- **Spring Boot 3.5** (Java 17) — Web, Validation, Data JPA
- **MySQL 8** — `ddl-auto: validate`로 운용합니다. 테이블은 애플리케이션이 만드는 게 아니라 [`db/schema.sql`](../db/schema.sql)로 직접 관리하고, 엔티티가 스키마와 어긋나면 기동 시점에 바로 에러로 알 수 있게 했습니다.
- **Spring Security Crypto** — 비밀번호 해시(BCrypt)만 필요해서, 필터 체인 전체가 따라오는 `spring-boot-starter-security` 대신 `spring-security-crypto` 모듈만 추가했습니다.
- **Lombok**, **Spring Boot DevTools** — 로컬 개발 시 코드 변경을 컴파일하면 자동 재기동됩니다.
- **KMDB Open API** — 영화 메타데이터(줄거리, 출연진, 포스터, 스틸컷) 원천
- **Gemini API** — AI 줄거리 요약, 챗봇 대화·예매 처리
- **토스페이먼츠 결제위젯** — 결제 연동(테스트 키)

## 📁 패키지 구조

도메인별 패키지(package-by-feature)로 구성했습니다. 각 패키지 안에 Controller/Service/Repository/Entity/DTO가 함께 있습니다.

```
com.moviepick.backend/
├── auth/              # 회원가입/로그인(아이디 찾기 포함), 내 정보 수정·탈퇴, 선호 장르, 관리자 회원 관리(접속 상태·정지)
├── oauth/             # 구글 OAuth2 소셜 로그인 — 인가 URL 생성, 콜백에서 토큰 교환, 1회용 세션 교환 토큰 발급
├── movie/             # 영화 조회·검색·필터링·자동완성 (KMDB 응답을 캐싱한 movies 테이블 기반)
├── kmdb/               # KMDB Open API 클라이언트, 응답 DTO, 전체 카탈로그 백그라운드 동기화
├── screening/          # 상영 일정 자동 생성, 좌석 배치, 좌석 임시 선점(hold) 및 TTL 만료 정리(남은 시간은 선점 당사자에게만 노출)
├── booking/            # 예매 생성/취소/좌석변경, 인원별 요금 계산, 예매 임박 알림, 관리자 예매 현황
├── payment/            # 토스페이먼츠 결제 승인/취소 연동
├── review/             # 리뷰 작성/수정/삭제, 신고(관리자 알림), "도움이 됐어요" 추천(작성자 알림, 중복 알림 방지), 평점 집계, 관리자 리뷰 관리
├── wishlist/           # 찜 목록
├── watched/            # 시청완료 표시
├── recentlyviewed/     # 최근 조회한 영화
├── chat/               # Gemini 기반 AI 챗봇 — 추천, 예매/취소/좌석변경을 대화로 처리(함수 호출형 도구 사용)
├── notification/       # 알림(예매 완료/임박, 리뷰 신고·추천 등) — 알림 종류별로 연결된 대상(예매/리뷰/신고 내역)을 함께 저장해 클릭 시 해당 화면으로 이동
├── appinfo/            # 앱 버전 조회
└── common/             # ApiException + GlobalExceptionHandler(공통 에러 응답), 한글 조사 유틸(KoreanJosa) 등
```

## 🧩 주요 설계 포인트

- **KMDB 응답을 로컬 DB에 캐싱**: 매 요청마다 외부 API를 부르지 않도록, 서버가 뜨면 KMDB 전체 카탈로그를 백그라운드로 스캔해 `movies` 테이블에 저장합니다(`kmdb`/`movie` 패키지). 그 덕분에 목록 조회·필터링·자동완성이 전부 로컬 DB 쿼리만으로 수십 ms 안에 응답합니다. 스캔이 끝나기 전에는 일부 집계가 추정치로 보일 수 있으나 자동으로 보정됩니다.
- **인증은 데모 수준**: 정식 토큰(JWT) 대신, 로그인한 사용자의 `id`를 `X-User-Id` 헤더로 받아 식별합니다. 운영 서비스 전환 시 가장 먼저 교체해야 할 부분입니다.
- **구글 소셜 로그인은 실제 OAuth2**: 인가 코드 흐름 자체는 표준대로 처리하지만, 로그인 상태는 여전히 쿠키/세션이 아니라 프런트가 들고 있는 `X-User-Id` 방식이라, 콜백이 사용자 id를 URL에 그대로 노출하지 않도록 60초짜리 1회용 교환 토큰을 발급하고 프런트가 그 토큰을 실제 사용자 정보로 바꿔가도록 설계했습니다(`oauth` 패키지).
- **좌석 동시성 제어**: 결제 중인 좌석은 TTL(5분)이 있는 "임시 선점(hold)" 상태로 표시되고, 만료되면 `SeatHoldCleanupService`가 자동으로 풀어줍니다. 예매 화면은 5초마다 선점을 갱신하며 남은 시간을 카운트다운으로 보여주고, 본인이 선점한 좌석에만 만료 시각을 내려받습니다(다른 사용자의 선점 시각은 노출하지 않음). 예매 확정 시에는 좌석 수와 결제 인원 수가 어긋나면 거부합니다.
- **예매 임박 알림**: `BookingReminderService`가 주기적으로 상영 시간을 확인해 상영 당일 아침과 상영 1시간 전에 알림을 보냅니다.
- **리뷰 신고/추천은 대칭 구조, 알림 대상만 다름**: 신고는 관리자 전원에게(알림 클릭 시 신고 상세 다이얼로그), 추천("도움이 됐어요")은 리뷰 작성자 본인에게만 알립니다. 추천은 설정/해제를 토글할 수 있지만, 같은 사람이 반복해서 설정해도 알림은 "이 사람이 이 리뷰에 알림을 보낸 적이 있는지"를 따로 기록해두고 평생 최초 1회만 보냅니다(`review_helpful_notifications` 테이블 — 추천 자체를 세는 `review_helpful_votes`와는 분리).
- **AI 챗봇의 예매 처리**: 단순 Q&A가 아니라, Gemini의 함수 호출(tool calling)로 실제 예매 생성/취소/좌석변경/찜 추가 같은 액션까지 대화로 수행합니다(`chat` 패키지).
- **공통 에러 응답**: 도메인 서비스는 `ApiException(message, HttpStatus)`만 던지면 되고, `GlobalExceptionHandler`가 일관된 형식(`{"message": "..."}`)의 응답으로 변환합니다.

## 🚀 로컬 실행

사전 준비(DB 생성, KMDB/Gemini API 키 설정)는 [최상위 README의 시작하기](../README.md#시작하기)를 먼저 따라주세요.

```bash
./gradlew bootRun       # Windows: gradlew.bat bootRun
```

`http://localhost:8080`에서 뜨며, 기동 로그에 KMDB 전체 카탈로그 스캔 진행 상황이 출력됩니다.

### 테스트

```bash
./gradlew test
```

### 코드만 바꾸고 재기동하고 싶을 때

Spring Boot DevTools가 붙어있어서, 실행 중인 상태에서 아래 명령만 다시 돌리면 변경사항이 반영되어 자동 재기동됩니다(서버를 직접 끄고 켤 필요 없음).

```bash
./gradlew classes       # 소스+리소스(application.yml 등) 변경을 함께 반영
```

## 🔑 참고 사항

- 결제는 토스페이먼츠가 공식 문서에서 공개한 **테스트 전용 키**로 연동되어 있어 실제 결제가 발생하지 않습니다.
- `application-local.yml`에는 실제 API 키가 들어가므로 `.gitignore`에 등록되어 있습니다. 새로 clone했다면 `application-local.example.yml`을 복사해서 키를 채우세요.
- DB 스키마를 바꿨다면 `db/schema.sql`도 함께 수정하고, `ddl-auto: validate` 특성상 엔티티와 스키마가 어긋나면 기동이 바로 실패한다는 점을 기억하세요.
