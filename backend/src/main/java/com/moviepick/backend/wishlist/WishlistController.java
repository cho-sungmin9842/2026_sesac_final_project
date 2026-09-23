package com.moviepick.backend.wishlist;

import com.moviepick.backend.wishlist.dto.WishlistDto;
import com.moviepick.backend.wishlist.dto.WishlistRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public List<WishlistDto> list(@RequestHeader("X-User-Id") Long userId) {
        return wishlistService.list(userId);
    }

    @GetMapping("/{movieId}")
    public boolean isWishlisted(@RequestHeader("X-User-Id") Long userId, @PathVariable String movieId) {
        return wishlistService.isWishlisted(userId, movieId);
    }

    @PostMapping
    public WishlistDto add(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody WishlistRequest request) {
        return wishlistService.add(userId, request);
    }

    @DeleteMapping("/{movieId}")
    public void remove(@RequestHeader("X-User-Id") Long userId, @PathVariable String movieId) {
        wishlistService.remove(userId, movieId);
    }
}
