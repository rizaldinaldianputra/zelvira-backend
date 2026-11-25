package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.ReviewRequest;
import com.zelvira.zelvira.dto.ReviewResponse;
import com.zelvira.zelvira.entity.Review;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.OrderItemRepository;
import com.zelvira.zelvira.repository.ReviewRepository;
import com.zelvira.zelvira.entity.OrderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;

    public ReviewService(ReviewRepository reviewRepository, OrderItemRepository orderItemRepository) {
        this.reviewRepository = reviewRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public ReviewResponse createReview(Long userId, ReviewRequest request) {
        Review review = Review.builder()
                .productId(request.getProductId())
                .userId(userId)
                .userName(request.getUserName())
                .rating(request.getRating())
                .comment(request.getComment())
                .isActive(true)
                .build();

        review.setIsActive(true);

        // Mark the order item as rated
        if (request.getOrderItemId() != null) {
            OrderItem orderItem = orderItemRepository.findById(request.getOrderItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Order item not found with id: " + request.getOrderItemId()));
            orderItem.setIsRated(true);
            orderItemRepository.save(orderItem);
        }

        return mapToResponse(reviewRepository.save(review));
    }

    @Transactional
    public ReviewResponse updateReviewStatus(Long id, Boolean isActive) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        review.setIsActive(isActive);
        return mapToResponse(reviewRepository.save(review));
    }

    public Page<ReviewResponse> getActiveReviewsByProduct(Long productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reviewRepository.findByProductIdAndIsActiveTrue(productId, pageable).map(this::mapToResponse);
    }

    public Page<ReviewResponse> getAllReviews(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reviewRepository.findAll(pageable).map(this::mapToResponse);
    }

    public void deleteReview(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        reviewRepository.delete(review);
    }

    private ReviewResponse mapToResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .productName(review.getProduct() != null ? review.getProduct().getName() : null)
                .userId(review.getUserId())
                .userName(review.getUserName())
                .rating(review.getRating())
                .comment(review.getComment())
                .isActive(review.getIsActive())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
