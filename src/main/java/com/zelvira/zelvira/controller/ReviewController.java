package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ReviewRequest;
import com.zelvira.zelvira.dto.ReviewResponse;
import com.zelvira.zelvira.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.zelvira.zelvira.security.CustomUserDetails userDetails,
            @RequestAttribute(value = "userId", required = false) Long attrUserId,
            @Valid @RequestBody ReviewRequest request) {
        Long finalUserId = userDetails != null ? userDetails.getId() : (attrUserId != null ? attrUserId : 1L);
        return new ResponseEntity<>(reviewService.createReview(finalUserId, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getAllReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.getAllReviews(page, size));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<ReviewResponse>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.getActiveReviewsByProduct(productId, page, size));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ReviewResponse> updateReviewStatus(
            @PathVariable Long id,
            @RequestParam Boolean isActive) {
        return ResponseEntity.ok(reviewService.updateReviewStatus(id, isActive));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }
}
