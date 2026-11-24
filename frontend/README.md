# 🎬 SESAC MOVIE — Frontend

React 19 + Vite 기반 SPA입니다. KMDB 영화 데이터 탐색, 좌석 예매, AI 챗봇/줄거리 요약, 마이페이지 등 [백엔드 API](../backend)가 제공하는 기능을 사용자에게 보여주는 화면을 담당합니다.

루트 저장소의 전체 소개와 백엔드 설정 방법은 [최상위 README](../README.md)를 참고하세요. 이 문서는 프론트엔드 코드를 작업할 때 필요한 내용만 다룹니다.

## 🛠 기술 스택

- **React 19** + **React Router 7** — SPA 라우팅은 `useSearchParams` 기반으로 필터/정렬/페이지 상태를 URL에 그대로 반영합니다(새로고침·뒤로가기에도 상태 유지).
- **Vite 8** — 개발 서버/번들러
- **Tailwind CSS 4** — 유틸리티 클래스 기반 스타일링, 다크모드는 `ThemeContext` + `data-theme` 속성으로 전환
- **Context API** — 별도 상태관리 라이브러리 없이 `AuthContext`(로그인 상태), `WishlistContext`(찜 목록), `ThemeContext`(다크/라이트)로 전역 상태를 관리
- **Web Speech API** — AI 줄거리 요약 읽어주기(TTS), 챗봇 음성 입력(STT). 둘 다 브라우저 내장 기능이라 별도 패키지/서버 비용이 없습니다.

## 📁 폴더 구조

```
src/
├── api/              # 백엔드 호출 함수(엔드포인트별로 파일 분리). httpClient.js가 공통 fetch 래퍼
├── auth/             # 로그인 상태(AuthContext), 보호 라우트(RequireAuth)
├── wishlist/         # 찜 목록 전역 상태(WishlistContext)
├── theme/            # 다크/라이트 테마 상태(ThemeContext)
├── components/
│   ├── common/       # MovieCard, ConfirmDialog, InfoDialog, MovieTicketDialog(모바일 티켓 QR) 등 여러 페이지에서 쓰는 공용 컴포넌트
│   ├── icons/        # SocialIcons(구글 등), EyeIcons(비밀번호 보기/숨기기) 같은 인라인 SVG 아이콘
│   └── layout/        # Header, AdminSidebar, NotificationBell/NotificationItem 등 레이아웃 조각
├── layouts/          # 페이지를 감싸는 레이아웃(MainLayout 등)
├── pages/            # 라우트 단위 화면. 각 폴더 = 한 라우트(+ 그 화면 전용 components/)
│   ├── Home/              # 홈(최근 조회, 지금 인기, 취향저격 추천)
│   ├── MovieList/         # 영화 목록(필터/정렬/페이지네이션)
│   ├── MovieDetail/       # 상세(AI 줄거리 요약, 스틸컷 갤러리, 리뷰 작성/신고/추천)
│   ├── RecentlyViewed/    # 최근 조회한 영화 전체보기
│   ├── AiChat/            # AI 챗봇(대화방 목록/메시지, 음성 입력)
│   ├── Booking/           # 좌석 선택/예매(선점 카운트다운), 예매 내역
│   ├── Payment/           # 토스페이먼츠 결제 성공/실패 리다이렉트 처리
│   ├── MyPage/            # 예매 내역(모바일 티켓), 찜/시청완료, 내 리뷰(추천 수 포함), 선호 장르
│   ├── Auth/              # 로그인(구글 소셜 로그인, 아이디/비밀번호 찾기)/회원가입
│   ├── Notifications/     # 알림 전체보기(지난 알림 포함, 페이지네이션)
│   └── Admin/             # 관리자: 리뷰 관리(검색/정렬/삭제 이력)·회원(접속 상태·정지) 관리, 상영관 현황, 예매 추이
└── utils/            # 포맷팅 등 순수 함수 유틸(한글 조사 처리, 알림 상대 시간 표시 등)
```

각 `pages/<Feature>/` 아래에는 그 화면에서만 쓰는 하위 컴포넌트를 모아두는 `components/` 폴더가 있습니다. 여러 화면에서 공유하는 컴포넌트만 최상위 `src/components/`로 뺍니다.

## 🚀 시작하기

### 사전 요구사항
- Node.js 20 이상
- [백엔드](../backend)가 `http://localhost:8080`에서 실행 중이어야 합니다(백엔드 설정은 [루트 README](../README.md) 참고).

### 설치 및 실행

```bash
npm install
npm run dev
```

`http://localhost:5173`에서 접속할 수 있습니다.

### 기타 명령어

```bash
npm run build    # 프로덕션 빌드 (dist/)
npm run preview  # 빌드 결과물 로컬 미리보기
npm run lint     # oxlint
```

### 환경 변수

`.env`에 API 서버 주소를 지정합니다(기본값은 `http://localhost:8080`이라 로컬 개발에서는 별도 설정 없이도 동작합니다).

```
VITE_API_BASE_URL=http://localhost:8080
```

## 🔑 참고 사항

- **인증**: 정식 토큰(JWT 등) 대신 로그인한 사용자의 `id`를 `X-User-Id` 헤더로 실어 보내는 데모 수준의 인증입니다(`api/httpClient.js`). 구글 로그인 버튼은 `<a>` 태그로 백엔드의 `/api/auth/oauth/google/start`를 직접 열어 실제 구글 인가 화면으로 이동하고, 완료되면 `/oauth/complete`로 돌아와 1회용 교환 토큰을 실제 로그인 사용자 정보로 바꿔 받습니다(`pages/Auth/OAuthCompletePage.jsx`).
- **로그인 상태 유지**: 로그인 화면의 체크박스에 따라 `localStorage`(브라우저를 껐다 켜도 유지) 또는 `sessionStorage`(탭을 닫으면 해제)에 로그인 정보를 저장합니다(`auth/AuthContext.jsx`).
- **AI 기능은 모두 선택적으로 동작**: 챗봇 음성 입력(STT)은 Web Speech API를 지원하지 않는 브라우저에서는 마이크 버튼이 아예 보이지 않고, 나머지 기능은 그대로 사용할 수 있습니다.
- **상태 관리**: Redux 같은 외부 라이브러리 없이 Context API + 각 페이지의 로컬 상태만으로 구성되어 있습니다. 여러 화면이 함께 알아야 하는 상태(로그인 여부, 찜 여부, 테마)만 Context로 올렸습니다.
