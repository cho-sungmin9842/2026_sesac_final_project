package com.moviepick.backend.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

// 상영관 마스터 데이터(1관/2관 고정 시딩, schema.sql 참고). 상영정보 자동 생성 배치가 이 목록을 그대로 씁니다.
@Entity
@Table(name = "theaters", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
public class Theater {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    protected Theater() {
    }

    public Theater(String name) {
        this.name = name;
    }
}
