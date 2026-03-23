package com.vedathrifts.repository;

import com.vedathrifts.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Find products with low stock
    Page<Product> findByStockLessThanEqual(Integer stock, Pageable pageable);
    
    // Find products by status 
    Page<Product> findByStatus(String status, Pageable pageable);
    
    // Find product by ID with status check
    Optional<Product> findByIdAndStatus(Long id, String status);
    
    // Filter products with multiple criteria
    @Query("SELECT p FROM Product p WHERE " +
           "(:category IS NULL OR p.category = :category) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
           "(:brand IS NULL OR p.brand = :brand) AND " +
           "(:condition IS NULL OR p.condition = :condition) AND " +
           "(:size IS NULL OR p.size = :size) AND " +
           "p.status = 'ACTIVE'")
    Page<Product> filterProducts(
            @Param("category") String category,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("brand") String brand,
            @Param("condition") String condition,
            @Param("size") String size,
            Pageable pageable);
    
    // Search products by name or description
    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')) AND " +
           "p.status = 'ACTIVE'")
    Page<Product> searchProducts(@Param("search") String search, Pageable pageable);
    
    // Get new arrivals 
    List<Product> findTop10ByOrderByCreatedAtDesc();
    
    // Update product rating when reviews change
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.rating = :rating, p.reviewCount = :reviewCount WHERE p.id = :productId")
    void updateRating(@Param("productId") Long productId, 
                      @Param("rating") Double rating, 
                      @Param("reviewCount") Integer reviewCount);
    
    // Find related products 
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.id != :excludeProductId AND p.status = 'ACTIVE'")
    List<Product> findRelatedProducts(
            @Param("category") String category, 
            @Param("excludeProductId") Long excludeProductId, 
            Pageable pageable);
    
    // Find products on sale 
    @Query("SELECT p FROM Product p WHERE p.originalPrice IS NOT NULL AND p.originalPrice > p.price AND p.status = 'ACTIVE'")
    Page<Product> findOnSaleProducts(Pageable pageable);
    
    // Find products by minimum rating
    @Query("SELECT p FROM Product p WHERE p.rating >= :minRating AND p.status = 'ACTIVE'")
    Page<Product> findByMinRating(@Param("minRating") Double minRating, Pageable pageable);
    
    // Find products by category 
    Page<Product> findByCategoryAndStatus(String category, String status, Pageable pageable);
    
    // Find products by brand
    Page<Product> findByBrandAndStatus(String brand, String status, Pageable pageable);
    
    // Find products by condition
    Page<Product> findByConditionAndStatus(String condition, String status, Pageable pageable);
    
    // Find products by size
    Page<Product> findBySizeAndStatus(String size, String status, Pageable pageable);
    
    // Find product by exact name 
    Optional<Product> findByName(String name);
    
    // Find products by name containing
    List<Product> findByNameContainingIgnoreCase(String name);
    
    // Count products by category 
    @Query("SELECT p.category, COUNT(p) FROM Product p WHERE p.status = 'ACTIVE' GROUP BY p.category")
    List<Object[]> countProductsByCategory();
    
    // Get all unique categories 
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL AND p.status = 'ACTIVE' ORDER BY p.category")
    List<String> findAllActiveCategories();
    
    // Get all categories including inactive 
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();
    
    // Count total active products
    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = 'ACTIVE'")
    long countActiveProducts();
    
    // Find products with exact category match 
    List<Product> findByCategoryAndStatus(String category, String status);
    
    // Find products with category containing text 
    @Query("SELECT p FROM Product p WHERE LOWER(p.category) LIKE LOWER(CONCAT('%', :category, '%')) AND p.status = 'ACTIVE'")
    List<Product> findByCategoryContaining(@Param("category") String category);
    
    // Find products with exact match and log the query 
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.status = 'ACTIVE'")
    List<Product> findActiveByCategoryExact(@Param("category") String category);
    
    // Get sample of products with their categories
    @Query("SELECT p.id, p.name, p.category, p.status FROM Product p WHERE p.status = 'ACTIVE' ORDER BY p.id")
    List<Object[]> getProductCategorySample(Pageable pageable);
    
    // ========== ADDED METHODS FOR ADMIN CONTROLLER ==========
    
    // Find all active products (status != 'DELETED')
    @Query("SELECT p FROM Product p WHERE p.status != 'DELETED'")
    Page<Product> findAllActive(Pageable pageable);
    
    // Find all active products with sorting
    @Query("SELECT p FROM Product p WHERE p.status != 'DELETED'")
    List<Product> findAllActive();
    
    // Find low stock active products (stock <= threshold and not deleted)
    @Query("SELECT p FROM Product p WHERE p.stock <= :threshold AND p.status != 'DELETED'")
    Page<Product> findByStockLessThanEqualActive(@Param("threshold") int threshold, Pageable pageable);
}