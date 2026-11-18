# 🎬 SESAC MOVIE

KMDB(한국영화데이터베이스) Open API 기반의 영화 예매 서비스입니다. 영화 탐색부터 좌석 예매·결제, AI 챗봇을 통한 대화형 예매, 리뷰·찜 목록까지 실제 영화관 예매 서비스에 준하는 기능을 구현했습니다.

[![Java](https://img.shields.io/badge/Java-17-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-8-646CFF)](https://vite.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1)](https://www.mysql.com/)

## 🎥 데모

<img src="docs/demo.gif" alt="SESAC MOVIE 데모" width="640" />

홈 → 영화 상세(AI 요약) → 좌석 예매 → AI 챗봇 → 마이페이지 → 관리자 화면 순으로 핵심 흐름을 빠르게 둘러볼 수 있습니다. 각 화면의 상세 스크린샷은 [아래 스크린샷 섹션](#스크린샷)에 있습니다.

## ✨ 주요 기능

### 로그인/회원가입
- **아이디/비밀번호 로그인** + **구글 소셜 로그인(실제 OAuth2 연동)** — 로그인 상태 유지(체크 시 브라우저를 껐다 켜도 유지, 안 하면 탭을 닫으면 해제)
- 아이디 찾기(닉네임+비밀번호), 비밀번호 찾기(아이디+닉네임, 임시 비밀번호 자동 발급), 비밀번호 입력 중 Caps Lock 켜짐 경고
- 회원가입 시 비밀번호 규칙 실시간 체크리스트(✓/✗), 비밀번호 확인 칸 실시간 일치 여부 표시

### 일반 사용자
- **영화 탐색**: 장르·개봉연도·러닝타임·연령등급 다중 필터, 최신순/이름순/평점순 정렬
- **헤더 검색 자동완성**: 영화 제목/배우/감독 기준 실시간 자동완성(최대 5개), 동명 영화는 개봉연도 최신순으로 표시
- **영화 상세**: AI 줄거리 요약(스포일러 방지, 음성으로 읽어주기 지원), 스틸컷 갤러리, 사용자 리뷰 작성/수정/삭제 및 정렬(최신순/평점순)
- **리뷰 상호작용**: 다른 사용자 리뷰에 "도움이 됐어요" 추천(작성자에게 알림, 중복 알림 방지) / 신고(관리자에게 알림)
- **마이페이지**: 예매 내역(캘린더/목록, 모바일 티켓 보기 — QR 코드 포함), 찜한 영화(지금 상영중 배지·필터), 시청완료, 내가 쓴 리뷰(추천받은 수 표시), 선호 장르 설정
- **홈 화면**: 최근 조회한 영화(전체보기 지원), 지금 인기 있는 영화, 선호 장르 기반 맞춤 추천
- **알림**: 예매/리뷰 추천 등 알림 벨 + 지난 알림까지 모두 보는 전체보기 페이지(페이지네이션)

### 예매
- 실시간 좌석 배치도(선점/예약 상태 반영), 인원 구분별 요금 자동 계산(주중/주말/공휴일·시간대 차등)
- 좌석 임시 선점(hold) 및 TTL 자동 만료, 선점 남은 시간 실시간 카운트다운 표시, 결제 중 좌석 충돌 방지, 1회 예매 최대 10명 제한
- 토스페이먼츠 결제위젯 연동, 예매 완료/취소/임박 알림, 예매 완료 시 모바일 티켓(QR 코드) 발급

### AI 챗봇
- Gemini 기반 자연어 대화로 영화 추천, 상영정보·좌석현황 조회, 예매·예매취소·좌석변경까지 전부 대화로 처리
- 조건(장르/연도/키워드) 기반 추천, 대화 맥락 유지("그거 예매해줘" 같은 지시 표현 처리)
- 음성 입력(STT)으로 말로 질문/예매, 대화방(세션) 관리 — 생성/목록/삭제

### 관리자
- 리뷰 관리(전체 리뷰 조회·삭제), 리뷰 신고 알림 클릭 시 신고 상세(리뷰 내용·작성자·사유·신고자) 다이얼로그로 바로 확인
- 회원 관리(활동 집계, 접속중/비접속중 상태 표시, 정지 사유 선택 및 사유 안내, 정지 시 즉시 로그아웃 처리)
- 상영관별 예매 현황판(좌석 배치도), 기간별 예매 추이 그래프

## 📸 스크린샷

<table>
<tr>
<td width="50%">

**홈 — 최근 조회 · 인기 · 맞춤 추천**
<img src="docs/screenshots/home.png" alt="홈 화면" width="100%" />

</td>
<td width="50%">

**헤더 검색 자동완성 (동명 영화는 최신연도순)**
<img src="docs/screenshots/search.png" alt="검색 자동완성" width="100%" />

</td>
</tr>
<tr>
<td width="50%">

**영화 상세 — AI 줄거리 요약(읽어주기) · 스틸컷 · 리뷰**
<img src="docs/screenshots/detail.png" alt="영화 상세" width="100%" />

</td>
<td width="50%">

**좌석 선택 — 실시간 좌석 배치도 · 인원별 요금**
<img src="docs/screenshots/seat.png" alt="좌석 선택" width="100%" />

</td>
</tr>
<tr>
<td width="50%">

**AI 챗봇 — 대화로 영화 추천/예매**
<img src="docs/screenshots/chat.png" alt="AI 챗봇" width="100%" />

</td>
<td width="50%">

**마이페이지 — 예매 내역 · 찜 · 선호 장르**
<img src="docs/screenshots/mypage.png" alt="마이페이지" width="100%" />

</td>
</tr>
</table>

**관리자 — 상영관별 좌석 현황 · 예매 추이 그래프**
<img src="docs/screenshots/admin.png" alt="관리자 화면" width="720" />

## 🛠 기술 스택

| 영역 | 스택 |
| --- | --- |
| 백엔드 | Java 17, Spring Boot 3.5(Web/Validation/Data JPA), MySQL 8, Gradle |
| 프론트엔드 | React 19, React Router 7, Vite 8, Tailwind CSS 4 |
| 외부 연동 | KMDB Open API, Gemini API(AI 요약·챗봇), 토스페이먼츠 결제위젯 |
| 인증 | 비밀번호 해시(BCrypt) + `X-User-Id` 헤더 기반 데모 인증, 구글 OAuth2 소셜 로그인 |

## 📁 프로젝트 구조

```
.
├── backend/    # Spring Boot REST API — 상세는 backend/README.md 참고
├── frontend/   # React + Vite SPA — 상세는 frontend/README.md 참고
└── db/         # schema.sql, docker-compose.yml(로컬 MySQL)
```

각 영역의 패키지/폴더 구조와 설계 포인트는 [backend/README.md](backend/README.md), [frontend/README.md](frontend/README.md)에 자세히 정리해뒀습니다.

## 🚀 시작하기

### 사전 요구사항
- JDK 17
- Node.js 20 이상
- MySQL 8 (Docker 또는 로컬 설치)
- [KMDB ServiceKey](https://www.kmdb.or.kr/info/api/apiDetail/6), [Gemini API 키](https://aistudio.google.com/apikey) (둘 다 무료 발급)

### 1. 데이터베이스 준비

**Docker 사용 시** (가장 간편):
```bash
cd db
docker compose up -d   # schema.sql이 최초 1회 자동 적용됩니다
```

**로컬 MySQL 사용 시**: `db/schema.sql`을 직접 실행해 테이블을 생성하세요.

### 2. 백엔드 API 키 설정

`backend/src/main/resources/application-local.yml` 파일을 만들고(또는 수정하고) 발급받은 키를 입력합니다. 이 파일은 `.gitignore`에 등록되어 있어 커밋되지 않습니다. `backend/src/main/resources/application-local.example.yml`을 복사해서 시작하면 편합니다.

```yaml
kmdb:
  service-key: 발급받은_KMDB_SERVICE_KEY

gemini:
  api-key: 발급받은_GEMINI_API_KEY
```

구글 소셜 로그인을 써보려면(선택 사항 — 설정하지 않아도 아이디/비밀번호 로그인은 그대로 됩니다), [Google Cloud Console](https://console.cloud.google.com/apis/credentials)에서 OAuth 클라이언트(웹 애플리케이션)를 만들고 승인된 리디렉션 URI에 `http://localhost:8080/api/auth/oauth/google/callback`을 등록한 뒤 아래 값도 채워주세요.

```yaml
oauth:
  google:
    client-id: 발급받은_GOOGLE_CLIENT_ID
    client-secret: 발급받은_GOOGLE_CLIENT_SECRET
```

### 3. 백엔드 실행

```bash
cd backend
./gradlew bootRun       # Windows: gradlew.bat bootRun
```

기본 포트는 `8080`입니다. 첫 실행 시 KMDB 전체 카탈로그를 백그라운드로 스캔해 캐싱하므로, 일부 필터 조합은 초기 몇 분간 정확한 건수가 아닌 추정치로 보일 수 있습니다(자동으로 보정됩니다).

### 4. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

기본적으로 `http://localhost:5173`에서 접속할 수 있고, `http://localhost:8080` API 서버를 바라봅니다(`frontend/.env`의 `VITE_API_BASE_URL`로 변경 가능).

### 5. 로그인

`db/schema.sql`에 기본 관리자 계정이 하나 포함되어 있습니다(보안상 계정 정보는 README에 적지 않습니다). 관리자로 로그인하려면 회원가입 후 DB에서 본인 계정의 `is_admin`을 `TRUE`로 바꾸거나, `db/schema.sql`의 관리자 계정 INSERT 구문을 원하는 아이디/비밀번호로 직접 수정해 적용하세요.

일반 사용자는 회원가입 화면에서 직접 가입해 이용할 수 있습니다.

---

## 🔑 참고 사항

- 결제는 토스페이먼츠가 공식 문서에서 공개한 **테스트 전용 키**로 연동되어 있어 실제 결제가 발생하지 않습니다.
- 인증은 정식 토큰(JWT 등) 대신 로그인한 사용자의 `id`를 `X-User-Id` 헤더로 전달하는 데모 수준으로 구현되어 있습니다. 구글 로그인만은 실제 OAuth2 인가 코드 흐름으로 동작하며, 콜백 이후에는 1회용 교환 토큰을 거쳐 같은 데모 세션으로 합류합니다.
- KMDB Open API 응답을 서버에서 캐싱·재가공하여 "전체 영화 N건" 같은 집계가 실제 목록과 항상 정확히 일치하도록 처리했습니다.
- AI 줄거리 요약 읽어주기(TTS)와 챗봇 음성 입력(STT)은 브라우저 내장 Web Speech API를 사용해 별도 서버 비용 없이 동작하며, 지원하지 않는 브라우저에서는 해당 버튼이 자동으로 숨겨집니다.
- 리뷰 신고/추천은 서로 대칭되는 구조로 설계했습니다 — 신고는 관리자에게, 추천("도움이 됐어요")은 리뷰 작성자에게 알림이 가며, 같은 사람이 추천을 설정/해제를 반복해도 알림은 평생 최초 1회만 전송됩니다.
