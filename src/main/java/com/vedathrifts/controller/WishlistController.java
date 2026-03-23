package com.vedathrifts.controller;

import com.vedathrifts.dto.request.WishlistItemRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.dto.response.WishlistItemResponse;
import com.vedathrifts.model.Product;
import com.vedathrifts.model.User;
import com.vedathrifts.model.Wishlist;
import com.vedathrifts.model.WishlistItem;
import com.vedathrifts.repository.ProductRepository;
import com.vedathrifts.repository.UserRepository;
import com.vedathrifts.repository.WishlistRepository;
import com.vedathrifts.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:3000", maxAge = 3600, allowCredentials = "true")
@RestController
@RequestMapping("/wishlist")
public class WishlistController {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    // Get user's wishlist
    @GetMapping
    public ResponseEntity<?> getWishlist(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            System.out.println("=== GET WISHLIST ===");
            
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }
            
            System.out.println("User ID: " + currentUser.getId());
            
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Wishlist wishlist = wishlistRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        Wishlist newWishlist = new Wishlist();
                        newWishlist.setUser(user);
                        return wishlistRepository.save(newWishlist);
                    });

            
            List<WishlistItemResponse> items = wishlist.getItems().stream()
                    .map(item -> {
                        // Fetch latest product details
                        Optional<Product> productOpt = productRepository.findById(item.getProductId());
                        if (productOpt.isPresent()) {
                            Product product = productOpt.get();
                            return new WishlistItemResponse(
                                item.getId(),
                                product.getId(),
                                product.getName(),
                                product.getPrice(),
                                product.getImages() != null && !product.getImages().isEmpty() 
                                    ? product.getImages().get(0) : item.getImageUrl()
                            );
                        } else {
                           
                            return WishlistItemResponse.fromEntity(item);
                        }
                    })
                    .collect(Collectors.toList());
            
            System.out.println("Wishlist items count: " + items.size());
            
            // Return wrapped response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", items);
            response.put("count", items.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error in getWishlist: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error fetching wishlist: " + e.getMessage()));
        }
    }

    // Add item to wishlist
    @PostMapping("/items")
    public ResponseEntity<?> addToWishlist(@Valid @RequestBody WishlistItemRequest itemRequest,
                                            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            System.out.println("=== ADD TO WISHLIST ===");
            System.out.println("Product ID: " + itemRequest.getProductId());
            
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }
            
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Wishlist wishlist = wishlistRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        Wishlist newWishlist = new Wishlist();
                        newWishlist.setUser(user);
                        return wishlistRepository.save(newWishlist);
                    });

            // Check if item already exists
            boolean exists = wishlist.getItems().stream()
                    .anyMatch(item -> item.getProductId().equals(itemRequest.getProductId()));

            if (exists) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Item already in wishlist");
                response.put("data", null);
                return ResponseEntity.ok(response);
            }

            // Get product details
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found with ID: " + itemRequest.getProductId()));

            WishlistItem newItem = new WishlistItem();
            newItem.setProductId(product.getId());
            newItem.setProductName(product.getName());
            newItem.setPrice(product.getPrice());
            
            // Get the first image from product images
            String imageUrl = null;
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                imageUrl = product.getImages().get(0);
                System.out.println("Using product image: " + imageUrl);
            } else {
                System.out.println("No images found for product: " + product.getId());
            }
            newItem.setImageUrl(imageUrl);
            
            newItem.setWishlist(wishlist);

            wishlist.getItems().add(newItem);
            wishlistRepository.save(wishlist);

            WishlistItemResponse itemResponse = new WishlistItemResponse(
                newItem.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                imageUrl
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item added to wishlist");
            response.put("data", itemResponse);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error in addToWishlist: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error adding to wishlist: " + e.getMessage()));
        }
    }

    // Remove item from wishlist
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<?> removeFromWishlist(@PathVariable Long productId,
                                                 @AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            System.out.println("=== REMOVE FROM WISHLIST ===");
            System.out.println("Product ID: " + productId);
            
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }
            
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Wishlist wishlist = wishlistRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Wishlist not found"));

            boolean removed = wishlist.getItems().removeIf(item -> item.getProductId().equals(productId));

            if (!removed) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Item not found in wishlist"));
            }

            wishlistRepository.save(wishlist);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item removed from wishlist");
            response.put("data", null);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error in removeFromWishlist: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error removing from wishlist: " + e.getMessage()));
        }
    }

    // ADD THIS ENDPOINT - Clear entire wishlist
    @DeleteMapping
    public ResponseEntity<?> clearWishlist(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            System.out.println("=== CLEAR WISHLIST ===");
            
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }
            
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Wishlist wishlist = wishlistRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Wishlist not found"));

            // Clear all items
            wishlist.getItems().clear();
            wishlistRepository.save(wishlist);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Wishlist cleared successfully");
            response.put("data", null);
            response.put("count", 0);
            
            System.out.println("Wishlist cleared for user: " + currentUser.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error in clearWishlist: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error clearing wishlist: " + e.getMessage()));
        }
    }

    // Check if item is in wishlist
    @GetMapping("/check/{productId}")
    public ResponseEntity<?> checkInWishlist(@PathVariable Long productId,
                                              @AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            if (currentUser == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", false);
                return ResponseEntity.ok(response);
            }
            
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Wishlist wishlist = wishlistRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        Wishlist newWishlist = new Wishlist();
                        newWishlist.setUser(user);
                        return wishlistRepository.save(newWishlist);
                    });

            boolean inWishlist = wishlist.getItems().stream()
                    .anyMatch(item -> item.getProductId().equals(productId));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", inWishlist);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error in checkInWishlist: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error checking wishlist: " + e.getMessage()));
        }
    }

    // Get wishlist count
    @GetMapping("/count")
    public ResponseEntity<?> getWishlistCount(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            if (currentUser == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", 0);
                return ResponseEntity.ok(response);
            }
            
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Wishlist wishlist = wishlistRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        Wishlist newWishlist = new Wishlist();
                        newWishlist.setUser(user);
                        return wishlistRepository.save(newWishlist);
                    });

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", wishlist.getItems().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error in getWishlistCount: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error getting wishlist count: " + e.getMessage()));
        }
    }
}