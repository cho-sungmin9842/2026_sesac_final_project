package com.moviepick.backend.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 예매 화면 "상영중인 영화" 목록의 날짜별 스냅샷입니다. 접속 날짜(snapshotDate) 기준으로 한 번 계산해두면,
 * 같은 날 다시 방문할 때는 KMDB를 다시 부르지 않고 이 목록(movie_id 순서 그대로)을 그대로 씁니다.
 */
@Entity
@Table(name = "now_showing_snapshots")
@IdClass(NowShowingSnapshotId.class)
@Getter
public class NowShowingSnapshot {

    @Id
    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;

    @Id
    @Column(name = "movie_id", length = 64)
    private String movieId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected NowShowingSnapshot() {
    }

    public NowShowingSnapshot(LocalDate snapshotDate, String movieId, int displayOrder) {
        this.snapshotDate = snapshotDate;
        this.movieId = movieId;
        this.displayOrder = displayOrder;
    }
}
