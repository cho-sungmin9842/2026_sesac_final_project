# syntax=docker/dockerfile:1

# ───────────── 1단계: Gradle 로 jar 빌드 ─────────────
# 이 프로젝트는 Java 17 + Spring Boot 3.5 + Gradle 9.7.1(wrapper) 입니다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# Windows 에서 받은 gradlew 의 줄바꿈(CRLF)과 실행 권한 문제를 막습니다("bad interpreter" 방지).
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src ./src
# 의존성/Gradle 배포판은 캐시 마운트에 보관해서 다시 빌드할 때 재사용합니다(이미지 레이어에는 남지 않음).
# 테스트는 이미지 빌드 중에는 돌리지 않습니다(테스트용 DB 접속이 필요한 것이 섞여 있음).
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon \
    && cp "$(find build/libs -name '*.jar' ! -name '*-plain.jar' | head -1)" /workspace/app.jar

# 안전장치: API 키가 들어 있는 application-local.yml 이 jar 에 섞여 들어갔으면 빌드를 실패시킵니다.
# (.dockerignore 로 이미 제외하지만, 누군가 제외 설정을 지워도 키가 이미지에 담겨 Docker Hub 로 나가는 사고를 막습니다.)
RUN if jar tf /workspace/app.jar | grep -q 'application-local'; then \
      echo "ERROR: application-local.yml(API 키 포함)이 jar 에 들어 있습니다. backend/.dockerignore 를 확인하세요." >&2; exit 1; \
    fi

# ───────────── 2단계: 실행 이미지(JRE 만) ─────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# root 가 아닌 전용 사용자로 실행합니다.
RUN groupadd --system app && useradd --system --gid app --home-dir /app app

COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R app:app /app
USER app

# 한국 시간 기준으로 동작해야 합니다. 상영정보 생성(매주 월요일 00:00), 상영 임박 알림(당일 08:00, 1시간 전)이
# JVM 기본 시간대를 쓰는데 컨테이너 기본값은 UTC 라서, 지정하지 않으면 9시간씩 어긋납니다.
# LANG/file.encoding 은 한글 파일/로그가 깨지지 않게 하는 설정입니다.
ENV TZ=Asia/Seoul \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8 -XX:MaxRAMPercentage=75.0"

# 설정은 코드/이미지에 넣지 않고 실행할 때 환경변수로 넘깁니다(아래 주석 참고).
#   SPRING_DATASOURCE_URL     DB 주소. 기본값이 localhost:3306 이라 컨테이너에서는 반드시 바꿔야 합니다.
#                             예) jdbc:mysql://db.cloud.local:3306/moviepick?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
#   DB_USERNAME / DB_PASSWORD DB 계정(기본값은 개발용이므로 운영에서는 반드시 바꿀 것)
#   KMDB_SERVICE_KEY          KMDB 서비스키. 없으면 영화 정보 조회와 주간 상영정보 생성이 실패합니다.
#   GEMINI_API_KEY            AI 챗봇/줄거리 요약용 키
#   TOSS_SECRET_KEY           (선택) 토스페이먼츠 시크릿 키. 없으면 코드의 공개 테스트 키가 쓰입니다.
#   APP_CORS_ALLOWED_ORIGINS  (선택) 프론트를 다른 주소에서 직접 호출할 때만 필요합니다.
EXPOSE 8080

# 포트가 열렸는지만 확인합니다(actuator 가 없어 /health 경로가 없음). 시작 직후 상영정보 생성이 도는 동안은 넉넉히 기다립니다.
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
