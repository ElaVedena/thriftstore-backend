package com.vedathrifts.service.impl;

import com.vedathrifts.dto.request.ReviewRequest;
import com.vedathrifts.dto.response.ReviewResponse;
import com.vedathrifts.dto.response.ReviewStatsResponse;
import com.vedathrifts.exception.ResourceNotFoundException;
import com.vedathrifts.exception.UnauthorizedException;
import com.vedathrifts.model.*;
import com.vedathrifts.repository.*;
import com.vedathrifts.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ReportedReviewRepository reportedReviewRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(Long userId, ReviewRequest request) {
        log.info("Creating review for product {} by user {}", request.getProductId(), userId);

        // Get user and product
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        // Check if user already reviewed this product
        if (reviewRepository.existsByUserIdAndProductId(userId, request.getProductId())) {
            throw new IllegalStateException("You have already reviewed this product");
        }

        // Check if user has purchased this product
        boolean hasPurchased = orderRepository.existsByUserIdAndProductIdAndStatusDelivered(userId, request.getProductId());

        // Create review
        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());
        review.setImages(request.getImages() != null ? request.getImages() : List.of());
        review.setVerified(hasPurchased);
        review.setHelpful(0);
        review.setNotHelpful(0);

        Review savedReview = reviewRepository.save(review);

        // Update product rating
        updateProductRating(product);

        log.info("Review created successfully with id: {}", savedReview.getId());
        return ReviewResponse.fromReview(savedReview);
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long reviewId, Long userId, ReviewRequest request) {
        log.info("Updating review {} by user {}", reviewId, userId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        // Check if user owns the review
        if (!review.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You can only update your own reviews");
        }

        // Update fields
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());
        if (request.getImages() != null) {
            review.setImages(request.getImages());
        }

        Review updatedReview = reviewRepository.save(review);

        // Update product rating
        updateProductRating(review.getProduct());

        log.info("Review updated successfully");
        return ReviewResponse.fromReview(updatedReview);
    }

    @Override
    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        log.info("Deleting review {} by user {}", reviewId, userId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        // Check if user owns the review or is admin
        if (!review.getUser().getId().equals(userId) && !isAdmin(userId)) {
            throw new UnauthorizedException("You can only delete your own reviews");
        }

        Product product = review.getProduct();
        reviewRepository.delete(review);

        // Update product rating
        updateProductRating(product);

        log.info("Review deleted successfully");
    }

    @Override
    public ReviewResponse getReviewById(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));
        return ReviewResponse.fromReview(review);
    }

    @Override
    public Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable) {
        log.info("Fetching reviews for product {}", productId);

        Page<Review> reviewsPage = reviewRepository.findByProductId(productId, pageable);
        
        return reviewsPage.map(ReviewResponse::fromReview);
    }

    @Override
    public Page<ReviewResponse> getFilteredReviews(Long productId, Integer rating, Boolean verified, String sortBy, Pageable pageable) {
        log.info("Fetching filtered reviews for product {} with rating {}, verified {}, sortBy {}", 
                productId, rating, verified, sortBy);

        // Apply sorting
        Pageable sortedPageable;
        switch (sortBy != null ? sortBy : "recent") {
            case "helpful":
                sortedPageable = PageRequest.of(
                    pageable.getPageNumber(), 
                    pageable.getPageSize(), 
                    Sort.by(Sort.Direction.DESC, "helpful")
                );
                break;
            case "highest":
                sortedPageable = PageRequest.of(
                    pageable.getPageNumber(), 
                    pageable.getPageSize(), 
                    Sort.by(Sort.Direction.DESC, "rating")
                );
                break;
            case "lowest":
                sortedPageable = PageRequest.of(
                    pageable.getPageNumber(), 
                    pageable.getPageSize(), 
                    Sort.by(Sort.Direction.ASC, "rating")
                );
                break;
            default: // recent
                sortedPageable = PageRequest.of(
                    pageable.getPageNumber(), 
                    pageable.getPageSize(), 
                    Sort.by(Sort.Direction.DESC, "createdAt")
                );
                break;
        }

        // Apply filters
        Page<Review> reviewsPage;
        if (rating != null) {
            reviewsPage = reviewRepository.findByProductIdAndRating(productId, rating, sortedPageable);
        } else if (verified != null && verified) {
            reviewsPage = reviewRepository.findByProductIdAndVerifiedTrue(productId, sortedPageable);
        } else {
            reviewsPage = reviewRepository.findByProductId(productId, sortedPageable);
        }

        return reviewsPage.map(ReviewResponse::fromReview);
    }

    @Override
    public ReviewStatsResponse getReviewStats(Long productId) {
        log.info("Calculating review stats for product {}", productId);

        // Get total reviews
        Long totalReviews = reviewRepository.countByProductId(productId);

        // Get average rating
        Double averageRating = reviewRepository.getAverageRatingByProductId(productId);

        // Get rating counts
        List<Object[]> ratingCountsRaw = reviewRepository.getRatingCountsByProductId(productId);
        Map<Integer, Integer> ratingCounts = new HashMap<>();
        
        // Initialize all ratings with 0
        for (int i = 1; i <= 5; i++) {
            ratingCounts.put(i, 0);
        }

        // Fill in actual counts
        for (Object[] row : ratingCountsRaw) {
            Integer rating = ((Number) row[0]).intValue();
            Long count = (Long) row[1];
            ratingCounts.put(rating, count.intValue());
        }

        return ReviewStatsResponse.builder()
                .averageRating(averageRating != null ? averageRating : 0.0)
                .totalReviews(totalReviews != null ? totalReviews.intValue() : 0)
                .ratingCounts(ratingCounts)
                .build();
    }

    @Override
    @Transactional
    public void markHelpful(Long reviewId, Long userId, Boolean helpful) {
        log.info("Marking review {} as {} by user {}", reviewId, helpful ? "helpful" : "not helpful", userId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        
        if (helpful) {
            review.setHelpful(review.getHelpful() + 1);
        } else {
            review.setNotHelpful(review.getNotHelpful() + 1);
        }

        reviewRepository.save(review);
    }

    @Override
    @Transactional
    public void reportReview(Long reviewId, Long userId, String reason) {
        log.info("Reporting review {} by user {} with reason: {}", reviewId, userId, reason);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        // Create reported review entry
        ReportedReview reportedReview = new ReportedReview();
        reportedReview.setReview(review);
        reportedReview.setReportedBy(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId)));
        reportedReview.setReason(reason);
        reportedReview.setStatus(ReportStatus.PENDING);

        reportedReviewRepository.save(reportedReview);
    }

    @Override
    public Page<ReviewResponse> getReportedReviews(Pageable pageable) {
        log.info("Fetching reported reviews for admin");

        Page<ReportedReview> reportedReviews = reportedReviewRepository.findByStatus(ReportStatus.PENDING, pageable);
        
        return reportedReviews.map(rr -> ReviewResponse.fromReview(rr.getReview()));
    }

    @Override
    @Transactional
    public void moderateReview(Long reviewId, String action, String reason) {
        log.info("Moderating review {} with action: {}, reason: {}", reviewId, action, reason);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        // Update reported review status
        List<ReportedReview> reportedReviews = reportedReviewRepository.findByReviewId(reviewId);
        for (ReportedReview rr : reportedReviews) {
            if (action.equalsIgnoreCase("approve")) {
                rr.setStatus(ReportStatus.REVIEWED);
            } else if (action.equalsIgnoreCase("reject")) {
                rr.setStatus(ReportStatus.REJECTED);
            }
            reportedReviewRepository.save(rr);
        }

        // If action is delete, remove the review
        if (action.equalsIgnoreCase("delete")) {
            Product product = review.getProduct();
            reviewRepository.delete(review);
            updateProductRating(product);
        }
    }

       
    @Override
    public List<ReviewResponse> getUserReviews(Long userId) {
        log.info("Fetching reviews for user {}", userId);
        
        // Check if user exists
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        
        // Find all reviews by user
        List<Review> reviews = reviewRepository.findByUserId(userId);
        
        return reviews.stream()
                .map(ReviewResponse::fromReview)
                .collect(Collectors.toList());
    }

    // Helper method to update product rating
    private void updateProductRating(Product product) {
        Double avgRating = reviewRepository.getAverageRatingByProductId(product.getId());
        Long reviewCount = reviewRepository.countByProductId(product.getId());
        
        product.setRating(avgRating != null ? avgRating : 0.0);
        product.setReviewCount(reviewCount != null ? reviewCount.intValue() : 0);
        
        productRepository.save(product);
    }

    // Helper method to check if user is admin
    private boolean isAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return user.getRole() == Role.ADMIN;
    }
}