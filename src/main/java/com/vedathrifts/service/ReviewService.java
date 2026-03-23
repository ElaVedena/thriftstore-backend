package com.vedathrifts.service;

import com.vedathrifts.dto.request.ReviewRequest;
import com.vedathrifts.dto.response.ReviewResponse;
import com.vedathrifts.dto.response.ReviewStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReviewService {
    
    // Existing methods
    ReviewResponse createReview(Long userId, ReviewRequest request);
    ReviewResponse updateReview(Long reviewId, Long userId, ReviewRequest request);
    void deleteReview(Long reviewId, Long userId);
    ReviewResponse getReviewById(Long reviewId);
    Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable);
    Page<ReviewResponse> getFilteredReviews(Long productId, Integer rating, Boolean verified, String sortBy, Pageable pageable);
    ReviewStatsResponse getReviewStats(Long productId);
    void markHelpful(Long reviewId, Long userId, Boolean helpful);
    void reportReview(Long reviewId, Long userId, String reason);
    Page<ReviewResponse> getReportedReviews(Pageable pageable);
    void moderateReview(Long reviewId, String action, String reason);
    
    // NEW METHOD
    List<ReviewResponse> getUserReviews(Long userId);
}