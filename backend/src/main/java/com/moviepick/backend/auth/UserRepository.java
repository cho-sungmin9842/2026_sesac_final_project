package com.moviepick.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByNickname(String nickname);

    // 예매 완료 알림을 전원에게 보낼 관리자 목록.
    List<User> findByAdminTrue();

    // 관리자 "회원 관리" 화면 - 최근 가입자부터 보여줍니다.
    List<User> findAllByOrderByCreatedAtDesc();

    // UserActivityInterceptor가 인증된 요청마다 호출합니다. staleBefore보다 최근에 이미 갱신했으면
    // 건너뛰어(= 조건절) 매 요청마다 쓰기가 일어나지 않고 최대 그 간격마다 한 번만 DB에 씁니다.
    // 정지된 계정(u.suspended = true)은 아예 갱신하지 않습니다 - 그래야 관리자가 정지시킨 뒤
    // clearLastActive로 지운 접속 시각이, 미처 로그아웃되지 않고 남아있던 요청 때문에 다시
    // "온라인"으로 되돌아가는 일이 없습니다(UserService.setSuspended 주석 참고).
    @Modifying
    @Query("UPDATE User u SET u.lastActiveAt = :now WHERE u.id = :id AND u.suspended = false AND (u.lastActiveAt IS NULL OR u.lastActiveAt < :staleBefore)")
    void touchLastActiveIfStale(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);

    // UserService.setSuspended가 정지 처리와 동시에 호출해, 2분 기준이 지나길 기다리지 않고 즉시
    // "오프라인"으로 보이게 합니다.
    @Modifying
    @Query("UPDATE User u SET u.lastActiveAt = NULL WHERE u.id = :id")
    void clearLastActive(@Param("id") Long id);
}
