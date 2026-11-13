package com.moviepick.backend.auth;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 100)
    private String nickname;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    // 관리자 "회원 관리" 화면의 정지 조치 - 정지된 계정은 로그인만 막히고(AuthService.login) 데이터는
    // 그대로 남아있어, 정지 해제하면 다시 예전처럼 쓸 수 있습니다.
    @Column(name = "is_suspended", nullable = false)
    private boolean suspended;

    // 정지 사유(SuspensionReason의 name()) - 정지할 때만 값이 들어가고, 해제하면 다시 null로 비웁니다.
    @Column(name = "suspension_reason", length = 50)
    private String suspensionReason;

    // 관리자 "회원 관리" 화면의 온라인/오프라인 표시용 - UserActivityInterceptor가 인증된 요청마다
    // 갱신합니다(엔티티를 직접 save하지 않고 UserRepository의 벌크 UPDATE로만 갱신하므로 세터는 없음).
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마이페이지 "선호 장르" 설정 - 목록/검색 필터가 쓰는 genre 문자열(예: "스릴러")을 그대로 저장합니다.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_preferred_genres", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "genre", length = 50)
    private Set<String> preferredGenres = new LinkedHashSet<>();

    protected User() {
    }

    public User(String username, String passwordHash, String nickname, boolean admin) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.admin = admin;
        this.createdAt = LocalDateTime.now();
    }

    public void updatePreferredGenres(Set<String> genres) {
        this.preferredGenres = new LinkedHashSet<>(genres);
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public void setSuspensionReason(String suspensionReason) {
        this.suspensionReason = suspensionReason;
    }
}
