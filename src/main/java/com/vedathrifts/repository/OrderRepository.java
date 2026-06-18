package com.vedathrifts.repository;

import com.vedathrifts.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Find orders by user ID with pagination
    Page<Order> findByUserId(Long userId, Pageable pageable);
    
    // Find orders by user ID sorted by date
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    // COUNT ORDERS BY USER ID
    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    Long countByUserId(@Param("userId") Long userId);
    
    // Find orders by status with pagination
    Page<Order> findByStatus(String status, Pageable pageable);
    
    // Find orders by multiple statuses (for revenue calculation)
    @Query("SELECT o FROM Order o WHERE o.status IN :statuses")
    List<Order> findByStatusIn(@Param("statuses") List<String> statuses);
    
    // Find orders by multiple statuses with date range (for revenue)
    @Query("SELECT o FROM Order o WHERE o.status IN :statuses AND o.createdAt BETWEEN :startDate AND :endDate")
    List<Order> findByStatusInAndCreatedAtBetween(
        @Param("statuses") List<String> statuses,
        @Param("startDate") java.time.LocalDateTime startDate,
        @Param("endDate") java.time.LocalDateTime endDate);
    
    // Find orders by status with user data (for admin)
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user WHERE o.status = :status")
    Page<Order> findByStatusWithUser(@Param("status") String status, Pageable pageable);
    
    // Find all orders with user data (for admin)
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user ORDER BY o.createdAt DESC")
    Page<Order> findAllWithUser(Pageable pageable);
    
    // Find order by order number (unique)
    Optional<Order> findByOrderNumber(String orderNumber);
    
    // Find order by checkout request ID 
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.checkoutRequestId = :checkoutRequestId")
    Optional<Order> findByCheckoutRequestIdWithItems(@Param("checkoutRequestId") String checkoutRequestId);
    
    Optional<Order> findByCheckoutRequestId(String checkoutRequestId);
    
    // Get total revenue from delivered orders
    @Query("SELECT SUM(o.total) FROM Order o WHERE o.status = 'DELIVERED'")
    Double getTotalRevenue();
    
    // Count orders by status
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Long countByStatus(@Param("status") String status);
    
    // Get order statistics by status
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> getOrderStatusCounts();
    
    // Find orders within date range
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    List<Order> findByDateRange(@Param("startDate") java.time.LocalDateTime startDate, 
                                @Param("endDate") java.time.LocalDateTime endDate);
    
    // Find orders within date range with user data
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user WHERE o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
    List<Order> findByDateRangeWithUser(@Param("startDate") java.time.LocalDateTime startDate, 
                                        @Param("endDate") java.time.LocalDateTime endDate);
    
    // Find pending payments 
    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING_PAYMENT' AND o.createdAt < :timeout")
    List<Order> findPendingPaymentsOlderThan(@Param("timeout") java.time.LocalDateTime timeout);
 
    @Query("SELECT COUNT(o) > 0 FROM Order o JOIN o.items i WHERE o.user.id = :userId AND i.product.id = :productId AND o.status = 'DELIVERED'")
    boolean existsByUserIdAndProductIdAndStatusDelivered(@Param("userId") Long userId, @Param("productId") Long productId);
    
    // Get revenue for a specific date range (delivered orders only)
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.status = 'DELIVERED' AND o.createdAt BETWEEN :startDate AND :endDate")
    Double getRevenueByDateRange(@Param("startDate") java.time.LocalDateTime startDate, 
                                 @Param("endDate") java.time.LocalDateTime endDate);
    
    // Get order count for a specific date range (delivered orders only)
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'DELIVERED' AND o.createdAt BETWEEN :startDate AND :endDate")
    Long getOrderCountByDateRange(@Param("startDate") java.time.LocalDateTime startDate, 
                                  @Param("endDate") java.time.LocalDateTime endDate);
}