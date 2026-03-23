package com.vedathrifts.controller;

import com.vedathrifts.dto.request.ProductRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.model.Product;
import com.vedathrifts.repository.ProductRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/products")
public class ProductController {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    
    @Autowired
    private ProductRepository productRepository;
    
    @GetMapping
    public ResponseEntity<Page<Product>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? Sort.by(sortBy).descending() 
                : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Product> products = productRepository.findByStatus("ACTIVE", pageable);
        
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElse(null);
        
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(product);
    }
    
    @GetMapping("/search")
    public ResponseEntity<Page<Product>> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productRepository.searchProducts(q, pageable);
        
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/filter")
    public ResponseEntity<Page<Product>> filterProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String size,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int sizeParam) {
        
        Pageable pageable = PageRequest.of(page, sizeParam);
        Page<Product> products = productRepository.filterProducts(
                category, minPrice, maxPrice, brand, condition, size, pageable);
        
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/new-arrivals")
    public ResponseEntity<List<Product>> getNewArrivals() {
        List<Product> products = productRepository.findTop10ByOrderByCreatedAtDesc();
        return ResponseEntity.ok(products);
    }
    
    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        try {
            logger.info("Creating new product: {}", productRequest.getName());
            
            Product product = new Product();
            
            // Map basic fields
            product.setName(productRequest.getName());
            product.setDescription(productRequest.getDescription());
            product.setPrice(productRequest.getPrice());
            product.setOriginalPrice(productRequest.getOriginalPrice());
            product.setStock(productRequest.getStock());
            product.setCategory(productRequest.getCategory());
            product.setBrand(productRequest.getBrand());
            product.setCondition(productRequest.getCondition());
            product.setSize(productRequest.getSize());
            product.setColor(productRequest.getColor());
            product.setMaterial(productRequest.getMaterial());
            product.setEra(productRequest.getEra());
            
            // Map images Cloudinary URLs
            if (productRequest.getImages() != null && !productRequest.getImages().isEmpty()) {
                product.setImages(productRequest.getImages());
                logger.info("Product has {} images", productRequest.getImages().size());
            } else {
                product.setImages(List.of()); 
                logger.warn("Product created with no images");
            }
            
            // Map available sizes
            if (productRequest.getAvailableSizes() != null) {
                product.setAvailableSizes(productRequest.getAvailableSizes());
            }
            
            // Set defaults
            product.setStatus("ACTIVE");
            product.setRating(0.0);
            product.setReviewCount(0);
            product.setCreatedAt(LocalDateTime.now());
            product.setUpdatedAt(LocalDateTime.now());
            
            Product savedProduct = productRepository.save(product);
            logger.info("Product saved with ID: {}", savedProduct.getId());
            
            return ResponseEntity.ok(new ApiResponse(true, "Product created successfully", savedProduct));
            
        } catch (Exception e) {
            logger.error("Failed to create product: ", e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Failed to create product: " + e.getMessage()));
        }
    }
    
    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, 
            @Valid @RequestBody ProductRequest productRequest) {
        
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
            
            logger.info("Updating product ID: {}", id);
            
            // Map basic fields
            product.setName(productRequest.getName());
            product.setDescription(productRequest.getDescription());
            product.setPrice(productRequest.getPrice());
            product.setOriginalPrice(productRequest.getOriginalPrice());
            product.setStock(productRequest.getStock());
            product.setCategory(productRequest.getCategory());
            product.setBrand(productRequest.getBrand());
            product.setCondition(productRequest.getCondition());
            product.setSize(productRequest.getSize());
            product.setColor(productRequest.getColor());
            product.setMaterial(productRequest.getMaterial());
            product.setEra(productRequest.getEra());
            
           
            if (productRequest.getImages() != null) {
                product.setImages(productRequest.getImages());
                logger.info("Updated images count: {}", productRequest.getImages().size());
            }
            
            // Map available sizes
            if (productRequest.getAvailableSizes() != null) {
                product.setAvailableSizes(productRequest.getAvailableSizes());
            }
            
            // Update status if provided
            if (productRequest.getStatus() != null) {
                product.setStatus(productRequest.getStatus());
            }
            
            product.setUpdatedAt(LocalDateTime.now());
            
            Product updatedProduct = productRepository.save(product);
            logger.info("Product updated successfully");
            
            return ResponseEntity.ok(new ApiResponse(true, "Product updated successfully", updatedProduct));
            
        } catch (Exception e) {
            logger.error("Failed to update product: ", e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Failed to update product: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
            
            logger.info("Soft deleting product ID: {}", id);
           
            product.setStatus("INACTIVE");
            product.setUpdatedAt(LocalDateTime.now());
            productRepository.save(product);
            
            return ResponseEntity.ok(new ApiResponse(true, "Product deleted successfully"));
            
        } catch (Exception e) {
            logger.error("Failed to delete product: ", e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Failed to delete product: " + e.getMessage()));
        }
    }
}