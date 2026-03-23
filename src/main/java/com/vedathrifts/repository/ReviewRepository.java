package com.vedathrifts.repository;

import com.vedathrifts.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    
    // Find reviews by product ID with pagination
    Page<Review> findByProductId(Long productId, Pageable pageable);
    
    // Find all reviews for a product
    List<Review> findByProductId(Long productId);
    
    // Find review by user and product 
    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);
    
    // Check if user has reviewed product
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    
    // Count reviews by product
    Long countByProductId(Long productId);
    
    // Get average rating for product
    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.product.id = :productId")
    Double getAverageRatingByProductId(@Param("productId") Long productId);
    
    // Get rating counts for product
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId GROUP BY r.rating")
    List<Object[]> getRatingCountsByProductId(@Param("productId") Long productId);
    
    // Find most helpful reviews
    Page<Review> findByProductIdOrderByHelpfulDesc(Long productId, Pageable pageable);
    
    // Find verified reviews
    Page<Review> findByProductIdAndVerifiedTrue(Long productId, Pageable pageable);
    
    // Find by rating
    Page<Review> findByProductIdAndRating(Long productId, Integer rating, Pageable pageable);
 


 List<Review> findByUserId(Long userId);
    
    // Find by date range
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.createdAt BETWEEN :startDate AND :endDate")
    List<Review> findByDateRange(@Param("productId") Long productId,
                                 @Param("startDate") LocalDateTime startDate,
                                 @Param("endDate") LocalDateTime endDate);
}