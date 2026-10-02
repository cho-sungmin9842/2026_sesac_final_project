package com.moviepick.backend.movie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NowShowingSnapshotRepository extends JpaRepository<NowShowingSnapshot, NowShowingSnapshotId> {
    List<NowShowingSnapshot> findBySnapshotDateOrderByDisplayOrderAsc(LocalDate snapshotDate);
}
