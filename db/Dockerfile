# syntax=docker/dockerfile:1

# MoviePick DB 이미지: 공식 MySQL 8.4 에 "문자셋 설정"과 "스키마(schema.sql)"를 담았습니다.
# docker-compose.yml 이 실행할 때 붙여 주던 설정(--character-set-server 옵션, schema.sql 마운트)을 이미지 안으로 옮긴 것입니다.
FROM mysql:8.4

# 문자셋을 utf8mb4 로 고정합니다.
#  - [mysqld]: 서버 기본 문자셋(compose 의 command: --character-set-server=... 와 같은 설정)
#  - [client]: 이 컨테이너 안에서 쓰는 mysql 클라이언트의 기본 문자셋. 컨테이너가 처음 만들어질 때 schema.sql 을
#              읽는 클라이언트도 이 설정을 따르므로, 한글 시드("1관" 등)가 "1ê´€"처럼 이중 인코딩되어 깨지는 문제를 막습니다.
RUN printf '%s\n' \
      '[mysqld]' \
      'character-set-server=utf8mb4' \
      'collation-server=utf8mb4_unicode_ci' \
      '' \
      '[client]' \
      'default-character-set=utf8mb4' \
      > /etc/mysql/conf.d/moviepick-charset.cnf \
 && chmod 644 /etc/mysql/conf.d/moviepick-charset.cnf

# 한국 시간 기준으로 동작하게 합니다(DB 기본값 CURRENT_TIMESTAMP 등이 한국 시간으로 저장). 백엔드(JDBC serverTimezone=Asia/Seoul)와 맞춥니다.
ENV TZ=Asia/Seoul

# 시작할 때 만들 데이터베이스와 애플리케이션 계정 이름(비밀번호는 아님).
# ★ 비밀번호는 이미지에 넣지 않고 실행할 때 환경변수로 넘깁니다:
#     MYSQL_ROOT_PASSWORD (필수), MYSQL_PASSWORD (moviepick 계정의 비밀번호)
ENV MYSQL_DATABASE=moviepick \
    MYSQL_USER=moviepick

# 스키마. mysql 이미지는 /docker-entrypoint-initdb.d/ 의 .sql 파일을 "데이터가 비어 있는 첫 시작 때 한 번만" 실행합니다
# (이미 데이터 볼륨에 DB 가 있으면 다시 실행되지 않습니다 - 스키마를 바꾼 뒤에는 기존 DB 에 직접 반영해야 합니다).
# ※ schema.sql 에는 기본 관리자 계정(admin / admin1234)이 시드로 들어 있습니다. 운영에 쓸 때는 비밀번호를 바꾸고,
#   이 이미지를 공개 저장소에 올릴지는 신중히 정하세요.
COPY --chmod=644 schema.sql /docker-entrypoint-initdb.d/01-schema.sql

# TCP(127.0.0.1)로 확인합니다. 소켓(-h localhost)으로 확인하면 안 됩니다: MySQL 은 "최초 초기화"(schema.sql 실행) 중에
# 임시 서버를 소켓으로만 열어 두는데(--skip-networking), 소켓 확인은 그때도 성공해서 아직 TCP 접속이 안 되는 상태를
# healthy 로 오판합니다. 그러면 depends_on(service_healthy)으로 기다리는 백엔드가 너무 일찍 시작해 "Communications
# link failure" 로 한 번 종료됩니다(2026-11-03 docker compose 시험에서 확인). TCP 확인은 초기화가 끝나고 진짜 서버가
# 올라온 뒤에만 성공합니다. root 비밀번호는 컨테이너에 넘긴 MYSQL_ROOT_PASSWORD 를 그대로 씁니다.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=10 \
  CMD mysqladmin ping -h 127.0.0.1 -u root -p"$MYSQL_ROOT_PASSWORD" --silent || exit 1

EXPOSE 3306
