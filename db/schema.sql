-- MOVIEPICK MySQL 스키마
-- 영화 정보 자체는 KMDB API에서 그때그때 받아오므로 movies 테이블은 없습니다.
-- 여기 테이블들은 KMDB가 주지 못하는 것(회원, 리뷰, 예매, 다운로드, 찜, 시청완료, 선호 장르 기록)만 저장합니다.
-- 백엔드 JPA 엔티티(User/Review/Booking/Download/Wishlist/WatchedMovie)와 컬럼이 1:1로 맞춰져 있습니다.

CREATE DATABASE IF NOT EXISTS moviepick
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE moviepick;

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

CREATE TABLE IF NOT EXISTS bookings (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    movie_id    VARCHAR(64) NOT NULL,
    movie_title VARCHAR(255) NOT NULL,
    theater     VARCHAR(50) NOT NULL,
    show_date   DATE        NOT NULL,
    showtime    VARCHAR(10) NOT NULL,
    total_price INT         NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- 같은 상영관/날짜/회차의 좌석이 이미 예약됐는지 조회할 때 씁니다.
    INDEX idx_bookings_showtime_key (movie_id, theater, show_date, showtime)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 예매 1건당 좌석 여러 개를 담는 자식 테이블(JPA @ElementCollection과 매핑).
CREATE TABLE IF NOT EXISTS booking_seats (
    booking_id BIGINT      NOT NULL,
    seat_code  VARCHAR(10) NOT NULL,
    PRIMARY KEY (booking_id, seat_code),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS downloads (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    movie_id      VARCHAR(64) NOT NULL,
    movie_title   VARCHAR(255) NOT NULL,
    poster_url    VARCHAR(500) NULL,
    size_mb       INT         NOT NULL DEFAULT 0,
    downloaded_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_downloads_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_downloads_user_movie UNIQUE (user_id, movie_id)
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
