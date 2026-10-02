-- MOVIEPICK MySQL 스키마
-- 영화 정보는 KMDB API에서 받아오되, 한 번이라도 조회된 것은 movies 테이블에 캐시해 다음부터는 KMDB를
-- 다시 부르지 않습니다(사용자가 실제로 조회한 만큼만 점진적으로 쌓이는 캐시입니다. 전체 카탈로그 일괄
-- 적재는 아닙니다).
-- 그 외 테이블들은 KMDB가 주지 못하는 것(회원, 리뷰, 예매, 찜, 시청완료, 선호 장르 기록)을 저장합니다.
-- 백엔드 JPA 엔티티(User/Review/Booking/Wishlist/WatchedMovie)와 컬럼이 1:1로 맞춰져 있습니다.

CREATE DATABASE IF NOT EXISTS moviepick
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE moviepick;

-- 회원 계정. 로그인은 이메일이 아니라 username(아이디) 기준이며, password_hash는 BCrypt 해시입니다.
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname      VARCHAR(100) NOT NULL,
    is_admin      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 기본 관리자 계정(아이디=admin, 비밀번호=admin1234). password_hash는 BCryptPasswordEncoder로 미리 해시해둔
-- 값이라 앱을 거치지 않고도 이 스키마만으로 계정이 만들어집니다. 이미 있으면(같은 username) 건너뛰므로
-- DB를 새로 만들 때나 이 스크립트를 다시 실행할 때나 항상 존재하고, 로그인 후 바뀐 비밀번호를 덮어쓰지 않습니다.
INSERT IGNORE INTO users (username, password_hash, nickname, is_admin)
VALUES ('admin', '$2a$10$rBUBitlzXedMq2u2Xpofx.zygLzu7lw5unS0UBRuK9CMw/EOJ0AbG', 'Admin', TRUE);

-- 마이페이지 "선호 장르" 설정 - 목록 화면의 genre 필터와 같은 문자열을 저장합니다(JPA @ElementCollection 매핑).
CREATE TABLE IF NOT EXISTS user_preferred_genres (
    user_id BIGINT      NOT NULL,
    genre   VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, genre),
    CONSTRAINT fk_user_preferred_genres_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 영화 상세 페이지 "사용자 리뷰"(평점 1~5점 + 텍스트). 목록/홈 화면의 평점 배지도 이 테이블을 집계해 만듭니다.
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- KMDB 합성 id 형식("{movieId}_{movieSeq}", 예: K_17748)을 그대로 저장합니다.
    movie_id    VARCHAR(64) NOT NULL,
    -- 마이페이지 "내가 쓴 리뷰" 탭에서 영화별로 KMDB를 다시 조회하지 않도록, 작성 시점 제목을 그대로 저장합니다.
    movie_title VARCHAR(255) NOT NULL,
    user_id     BIGINT      NOT NULL,
    score       INT         NOT NULL,
    content     TEXT        NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NULL,
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_reviews_score CHECK (score BETWEEN 1 AND 5),
    INDEX idx_reviews_movie_id (movie_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 마이페이지 "찜한 영화" 통계/탭.
CREATE TABLE IF NOT EXISTS wishlists (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    movie_id    VARCHAR(64) NOT NULL,
    movie_title VARCHAR(255) NOT NULL,
    poster_url  VARCHAR(500) NULL,
    added_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wishlists_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_wishlists_user_movie UNIQUE (user_id, movie_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 마이페이지 "시청완료" 통계/탭.
CREATE TABLE IF NOT EXISTS watched_movies (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    movie_id    VARCHAR(64) NOT NULL,
    movie_title VARCHAR(255) NOT NULL,
    poster_url  VARCHAR(500) NULL,
    watched_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_watched_movies_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_watched_movies_user_movie UNIQUE (user_id, movie_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- AI 추천 채팅 내역. movie_ids는 그 메시지가 추천한 영화들의 합성 id("{movieId}_{movieSeq}")를 콤마로 이어붙인
-- 값으로, 화면 재방문 시 KMDB에서 다시 상세를 조회해 추천 카드를 그대로 복원하는 데 씁니다(추천이 아닌
-- 일반 텍스트 메시지는 NULL).
CREATE TABLE IF NOT EXISTS chat_messages (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(10) NOT NULL,
    content    TEXT        NOT NULL,
    movie_ids  VARCHAR(500) NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_messages_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_chat_messages_user_id (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 영화 상세의 "AI 줄거리 요약(스포방지)" 결과 캐시. movie_id는 목록/상세가 쓰는 합성 id("{movieId}_{movieSeq}")
-- 그대로입니다. 같은 영화는 서버가 몇 번을 재시작해도 Gemini를 다시 호출하지 않도록 DB에 영구 저장합니다
-- (기존엔 메모리 캐시뿐이라 서버 재시작마다 전부 날아가고 다시 Gemini를 호출했습니다).
CREATE TABLE IF NOT EXISTS ai_summaries (
    movie_id   VARCHAR(64) PRIMARY KEY,
    summary    TEXT        NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- KMDB에서 받아온 영화 정보 캐시. movie_id는 목록/상세가 쓰는 합성 id("{movieId}_{movieSeq}")입니다.
-- 검색/목록 결과가 내려올 때는 요약 필드만 채워지고(has_detail=FALSE), 상세 페이지를 실제로 열어봐야
-- 나머지 상세 필드(plot/actors/trailers 등)까지 채워집니다(has_detail=TRUE). 평점/리뷰수는 리뷰 테이블에서
-- 항상 실시간으로 계산하므로 여기 저장하지 않습니다.
CREATE TABLE IF NOT EXISTS movies (
    movie_id        VARCHAR(64)  PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    english_title   VARCHAR(255) NULL,
    year            INT          NULL,
    genres          VARCHAR(255) NULL,
    runtime_minutes INT          NULL,
    age_rating      VARCHAR(50)  NULL,
    poster_url      VARCHAR(500) NULL,
    nation          VARCHAR(100) NULL,
    company         VARCHAR(255) NULL,
    directors       VARCHAR(500) NULL,
    actors          TEXT         NULL,
    plot            TEXT         NULL,
    poster_urls     TEXT         NULL,
    still_urls      TEXT         NULL,
    keywords        VARCHAR(500) NULL,
    trailers        TEXT         NULL,
    has_detail      BOOLEAN      NOT NULL DEFAULT FALSE,
    cached_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 예매 화면 "상영중인 영화" 목록의 접속 날짜별 스냅샷. 실제 상영 스케줄 API가 없어 "최근 4주 개봉작"을
-- 대신 쓰는데, 이 계산을 하루에 한 번만 하고(첫 방문 시 KMDB 조회 후 저장) 같은 날 재방문 시에는
-- 이 스냅샷과 movies 캐시만으로 서빙합니다.
CREATE TABLE IF NOT EXISTS now_showing_snapshots (
    snapshot_date DATE        NOT NULL,
    movie_id      VARCHAR(64) NOT NULL,
    display_order INT         NOT NULL,
    PRIMARY KEY (snapshot_date, movie_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 상영관 마스터 데이터. 1관/2관 두 개만 고정으로 존재합니다.
CREATE TABLE IF NOT EXISTS theaters (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_theaters_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT IGNORE INTO theaters (name) VALUES ('1관'), ('2관');

-- 상영정보. 매주 월요일 자정 배치(ScreeningGenerationService)가 그 주(월~일) 분량을 상영중인 영화 목록으로
-- 자동 생성합니다. movie_id는 다른 도메인과 동일하게 movies 테이블의 합성 id("{movieId}_{movieSeq}")입니다.
CREATE TABLE IF NOT EXISTS screenings (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id   VARCHAR(64) NOT NULL,
    theater_id BIGINT      NOT NULL,
    date       DATE        NOT NULL,
    start_time TIME        NOT NULL,
    end_time   TIME        NOT NULL,
    CONSTRAINT fk_screenings_movie FOREIGN KEY (movie_id) REFERENCES movies (movie_id),
    CONSTRAINT fk_screenings_theater FOREIGN KEY (theater_id) REFERENCES theaters (id),
    INDEX idx_screenings_movie_date (movie_id, date),
    INDEX idx_screenings_theater_date_start (theater_id, date, start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 상영 1건당 좌석(8행 A~H x 14열 = 112석, A열은 전부 휠체어석). 상영정보 생성 배치가 상영정보와 함께
-- 만들고, 실제 예매 상황을 흉내 내려고 약 15%는 미리 BOOKED로 채워둡니다.
CREATE TABLE IF NOT EXISTS seats (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    screening_id BIGINT      NOT NULL,
    row_label    VARCHAR(1)  NOT NULL,
    col_no       INT         NOT NULL,
    seat_type    VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    CONSTRAINT fk_seats_screening FOREIGN KEY (screening_id) REFERENCES screenings (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 영화 예매 내역. 상영관/날짜/시간/영화 제목은 더 이상 문자열로 중복 저장하지 않고 screening_id 하나로만
-- 연결합니다(screenings를 movies/theaters와 조인하면 전부 구할 수 있습니다). 좌석도 seats.id를 그대로
-- 참조합니다(좌석 종류는 seats.seat_type에 이미 있으므로 여기서 또 저장하지 않습니다).
CREATE TABLE IF NOT EXISTS bookings (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT   NOT NULL,
    screening_id BIGINT   NOT NULL,
    total_price  INT      NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookings_screening FOREIGN KEY (screening_id) REFERENCES screenings (id),
    INDEX idx_bookings_screening (screening_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 예매 1건당 실제 좌석(seats.id)과, 그 좌석을 산 사람의 인원 구분(성인/청소년/어린이/우대)을 저장합니다.
-- 한 좌석은 동시에 두 예매에 속할 수 없으므로 seat_id에 유니크 제약을 둡니다.
CREATE TABLE IF NOT EXISTS booking_seats (
    booking_id      BIGINT      NOT NULL,
    seat_id         BIGINT      NOT NULL,
    ticket_category VARCHAR(20) NOT NULL,
    PRIMARY KEY (booking_id, seat_id),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_seats_seat FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT uq_booking_seats_seat UNIQUE (seat_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 예매 완료 알림. user_id는 "알림을 받는 사람"입니다 - 예매한 본인에게 1건, 관리자(is_admin=TRUE) 전원에게
-- 각 1건씩 생성합니다(booking_id로 원래 예매를 참조, 예매가 지워지면 알림도 같이 지워집니다).
CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    message    VARCHAR(500) NOT NULL,
    booking_id BIGINT       NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    INDEX idx_notifications_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
