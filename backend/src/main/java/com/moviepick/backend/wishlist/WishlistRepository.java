package com.moviepick.backend.wishlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    List<Wishlist> findByUserIdOrderByAddedAtDesc(Long userId);

    boolean existsByUserIdAndMovieId(Long userId, String movieId);

    void deleteByUserIdAndMovieId(Long userId, String movieId);
}
