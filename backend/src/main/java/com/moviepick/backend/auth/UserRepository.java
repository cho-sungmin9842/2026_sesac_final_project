package com.moviepick.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // 예매 완료 알림을 전원에게 보낼 관리자 목록.
    List<User> findByAdminTrue();

    // 관리자 "회원 관리" 화면 - 최근 가입자부터 보여줍니다.
    List<User> findAllByOrderByCreatedAtDesc();
}
