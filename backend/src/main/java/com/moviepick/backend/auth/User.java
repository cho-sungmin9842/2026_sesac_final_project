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

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

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
}
