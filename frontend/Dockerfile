# syntax=docker/dockerfile:1

# ───────────── 1단계: React(Vite) 빌드 ─────────────
# Vite 8은 Node 20.19 이상 / 22.12 이상이 필요합니다(Node 18에서는 빌드가 실패합니다).
FROM node:22-alpine AS build
WORKDIR /app

# 의존성 설치를 소스 복사보다 먼저 해서, 소스만 바뀌었을 때는 이 단계 캐시를 재사용합니다.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# 브라우저가 호출할 API 주소(빌드할 때 번들에 고정됩니다). 빈 값이면 "접속한 주소의 /api/..." 로 호출합니다.
#  - 같은 도메인에서 nginx(이 이미지)나 Ingress가 /api 를 백엔드로 넘겨주는 구조라 기본값은 빈 값입니다.
#  - 다른 주소를 쓰려면: docker build --build-arg VITE_API_BASE_URL=https://api.example.com ...
# (.env 는 .dockerignore 로 제외했습니다. 안 그러면 .env 의 http://localhost:8080 이 번들에 그대로 박힙니다.)
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ───────────── 2단계: nginx 로 정적 파일 서빙 ─────────────
FROM nginx:1.27-alpine

# /etc/nginx/templates/*.template 은 컨테이너 시작 때 환경변수(${API_UPSTREAM} 등)를 채워
# /etc/nginx/conf.d/ 로 변환됩니다(nginx 공식 이미지 기능).
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

# /api/ 요청을 보낼 백엔드 주소. docker compose 에서는 서비스 이름(backend), 쿠버네티스에서는 Service 이름을 씁니다.
# 실행할 때 -e API_UPSTREAM=http://다른주소:8080 으로 바꿀 수 있습니다.
ENV API_UPSTREAM=http://backend:8080

# nginx.conf.template 의 resolver 에 쓰는 DNS 서버 주소(${NGINX_LOCAL_RESOLVERS})를 /etc/resolv.conf 에서 자동으로
# 채워 주는 nginx 이미지 기능을 켭니다. 이 값이 없으면 치환이 안 돼 nginx 가 시작하지 못합니다. Docker 네트워크든
# 쿠버네티스(CoreDNS)든 그 환경의 DNS 를 그대로 쓰므로 주소를 하드코딩하지 않아도 됩니다.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
