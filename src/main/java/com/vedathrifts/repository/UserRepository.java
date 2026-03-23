package com.vedathrifts.repository;

import com.vedathrifts.model.User;
import com.vedathrifts.model.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Find by email
    Optional<User> findByEmail(String email);
    
    // Check if email exists
    Boolean existsByEmail(String email);
    
    // Find users by role with pagination
    Page<User> findByRole(Role role, Pageable pageable);
    
    // Search users by name or email
    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);
    
    // Count users by role
    long countByRole(Role role);
    
    // Count users by creation date
    long countByCreatedAtAfter(LocalDateTime date);
    
    // Count orders by user ID
    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    Long countByUserId(@Param("userId") Long userId);
    
    // Check if admin exists
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.role = 'ADMIN'")
    boolean existsAdmin();
    
    // Get the admin user (should only be one)
    @Query("SELECT u FROM User u WHERE u.role = 'ADMIN'")
    Optional<User> findAdminUser();
    
    // Find active users
    @Query("SELECT u FROM User u WHERE u.isActive = true")
    Page<User> findActiveUsers(Pageable pageable);
    
    // Find inactive users
    @Query("SELECT u FROM User u WHERE u.isActive = false")
    Page<User> findInactiveUsers(Pageable pageable);
    
    // Count active users
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();
    
    // Count inactive users
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = false")
    long countInactiveUsers();
    
    // Advanced search with multiple fields
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "u.phone LIKE CONCAT('%', :search, '%')")
    Page<User> advancedSearch(@Param("search") String search, Pageable pageable);
    
    // Find users registered in last N days
    @Query("SELECT u FROM User u WHERE u.createdAt >= :date")
    Page<User> findUsersRegisteredAfter(@Param("date") LocalDateTime date, Pageable pageable);
    
    // Count users registered in last N days
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :date")
    long countUsersRegisteredAfter(@Param("date") LocalDateTime date);
}