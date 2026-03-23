package com.vedathrifts.controller;

import com.vedathrifts.dto.request.ReviewRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.dto.response.ReviewResponse;
import com.vedathrifts.dto.response.ReviewStatsResponse;
import com.vedathrifts.security.CurrentUser;
import com.vedathrifts.security.UserDetailsImpl;
import com.vedathrifts.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ReviewController {

    private final ReviewService reviewService;
    
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createReview(@CurrentUser UserDetailsImpl currentUser,
                                          @Valid @RequestBody ReviewRequest request) {
        ReviewResponse review = reviewService.createReview(currentUser.getId(), request);
        return ResponseEntity.ok(new ApiResponse(true, "Review submitted successfully", review));
    }

    @PutMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateReview(@CurrentUser UserDetailsImpl currentUser,
                                          @PathVariable Long reviewId,
                                          @Valid @RequestBody ReviewRequest request) {
        ReviewResponse review = reviewService.updateReview(reviewId, currentUser.getId(), request);
        return ResponseEntity.ok(new ApiResponse(true, "Review updated successfully", review));
    }

    @DeleteMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteReview(@CurrentUser UserDetailsImpl currentUser,
                                          @PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId, currentUser.getId());
        return ResponseEntity.ok(new ApiResponse(true, "Review deleted successfully"));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<?> getReview(@PathVariable Long reviewId) {
        ReviewResponse review = reviewService.getReviewById(reviewId);
        return ResponseEntity.ok(review);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(defaultValue = "recent") String sortBy) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ReviewResponse> reviews = reviewService.getFilteredReviews(productId, rating, verified, sortBy, pageable);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/product/{productId}/stats")
    public ResponseEntity<?> getReviewStats(@PathVariable Long productId) {
        ReviewStatsResponse stats = reviewService.getReviewStats(productId);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/{reviewId}/helpful")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markHelpful(@CurrentUser UserDetailsImpl currentUser,
                                         @PathVariable Long reviewId,
                                         @RequestParam Boolean helpful) {
        reviewService.markHelpful(reviewId, currentUser.getId(), helpful);
        return ResponseEntity.ok(new ApiResponse(true, "Thank you for your feedback"));
    }

    @PostMapping("/{reviewId}/report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> reportReview(@CurrentUser UserDetailsImpl currentUser,
                                          @PathVariable Long reviewId,
                                          @RequestParam String reason) {
        reviewService.reportReview(reviewId, currentUser.getId(), reason);
        return ResponseEntity.ok(new ApiResponse(true, "Review reported successfully"));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserReviews(@PathVariable Long userId) {
        List<ReviewResponse> reviews = reviewService.getUserReviews(userId);
        return ResponseEntity.ok(reviews);
    }
    
    @GetMapping("/admin/reported")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getReportedReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ReviewResponse> reviews = reviewService.getReportedReviews(pageable);
        return ResponseEntity.ok(reviews);
    }

    @PostMapping("/admin/{reviewId}/moderate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> moderateReview(@PathVariable Long reviewId,
                                            @RequestParam String action,
                                            @RequestParam(required = false) String reason) {
        reviewService.moderateReview(reviewId, action, reason);
        return ResponseEntity.ok(new ApiResponse(true, "Review moderated successfully"));
    }
}