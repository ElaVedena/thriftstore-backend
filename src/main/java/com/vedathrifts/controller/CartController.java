package com.vedathrifts.controller;

import com.vedathrifts.dto.request.CartItemRequest;
import com.vedathrifts.dto.request.MergeCartRequest;
import com.vedathrifts.dto.request.UpdateCartRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.dto.response.CartResponse;
import com.vedathrifts.dto.response.CartItemResponse;
import com.vedathrifts.model.Cart;
import com.vedathrifts.model.CartItem;
import com.vedathrifts.model.User;
import com.vedathrifts.repository.CartRepository;
import com.vedathrifts.repository.ProductRepository;
import com.vedathrifts.repository.UserRepository;
import com.vedathrifts.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

@CrossOrigin(origins = "http://localhost:3000", maxAge = 3600, allowCredentials = "true")
@RestController
@RequestMapping("/cart")
public class CartController {

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    // Get user's cart
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getCart(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        logger.info("========== GET CART ==========");
        logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "null");
        
        try {
            if (currentUser == null) {
                logger.warn("Unauthenticated access attempt to /cart");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }

            logger.info("Current user ID: {}", currentUser.getId());
            
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found with ID: " + currentUser.getId()));

            logger.info("User found: {}", user.getEmail());
            
            Cart cart = cartRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        logger.info("Creating new cart for user: {}", user.getId());
                        Cart newCart = new Cart();
                        newCart.setUser(user);
                        return cartRepository.save(newCart);
                    });

            // Force initialization of items
            int itemCount = cart.getItems().size();
            logger.info("Cart ID: {}, Items count: {}", cart.getId(), itemCount);
            
            if (itemCount > 0) {
                logger.info("Items in cart:");
                for (CartItem item : cart.getItems()) {
                    logger.info("  - Item ID: {}, Product: {}, Quantity: {}", 
                        item.getId(), item.getProductId(), item.getQuantity());
                }
            }

            CartResponse cartResponse = CartResponse.fromCart(cart);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cart", cartResponse);
            response.put("itemCount", cartResponse.getItemCount());

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error fetching cart: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error fetching cart: " + e.getMessage()));
        }
    }

    // Add item to cart
    @PostMapping("/items")
    @Transactional
    public ResponseEntity<?> addToCart(@Valid @RequestBody CartItemRequest itemRequest,
                                        @AuthenticationPrincipal UserDetailsImpl currentUser) {
        logger.info("========== ADD TO CART ==========");
        logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "null");
        logger.info("Product ID: {}", itemRequest.getProductId());
        logger.info("Product Name: {}", itemRequest.getProductName());
        logger.info("Price: {}", itemRequest.getPrice());
        logger.info("Quantity: {}", itemRequest.getQuantity());
        logger.info("Size: {}", itemRequest.getSize());
        logger.info("Image URL: {}", itemRequest.getImageUrl());
        
        try {
            if (currentUser == null) {
                logger.warn("User not authenticated");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }

            // Validate product exists
            if (!productRepository.existsById(itemRequest.getProductId())) {
                logger.warn("Product not found with ID: {}", itemRequest.getProductId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse(false, "Product not found with ID: " + itemRequest.getProductId()));
            }

            logger.info("Looking up user with ID: {}", currentUser.getId());
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            logger.info("User found: {}", user.getEmail());

            logger.info("Looking up cart for user ID: {}", user.getId());
            Cart cart = cartRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        logger.info("Creating new cart for user: {}", user.getId());
                        Cart newCart = new Cart();
                        newCart.setUser(user);
                        return cartRepository.save(newCart);
                    });
            
            logger.info("Cart ID: {}, Current items count before update: {}", cart.getId(), cart.getItems().size());

            // Check if item already exists in cart
            Optional<CartItem> existingItem = cart.getItems().stream()
                    .filter(item -> item.getProductId().equals(itemRequest.getProductId()) &&
                            (item.getSize() == null ? itemRequest.getSize() == null : 
                             item.getSize().equals(itemRequest.getSize())))
                    .findFirst();

            CartItem itemToSave;
            
            if (existingItem.isPresent()) {
                // Update quantity
                itemToSave = existingItem.get();
                int newQuantity = itemToSave.getQuantity() + (itemRequest.getQuantity() != null ? itemRequest.getQuantity() : 1);
                itemToSave.setQuantity(newQuantity);
                logger.info("Updated existing item - ID: {}, New quantity: {}", itemToSave.getId(), newQuantity);
            } else {
                // Add new item
                itemToSave = new CartItem();
                itemToSave.setProductId(itemRequest.getProductId());
                itemToSave.setProductName(itemRequest.getProductName());
                itemToSave.setPrice(itemRequest.getPrice());
                itemToSave.setQuantity(itemRequest.getQuantity() != null ? itemRequest.getQuantity() : 1);
                itemToSave.setSize(itemRequest.getSize());
                itemToSave.setImageUrl(itemRequest.getImageUrl());
                itemToSave.setCart(cart);
                cart.getItems().add(itemToSave);
                logger.info("Created new item for product: {}", itemRequest.getProductId());
            }

            logger.info("Saving cart with {} items", cart.getItems().size());
            Cart savedCart = cartRepository.save(cart);
            logger.info("Cart saved successfully with ID: {}, Items count after save: {}", 
                savedCart.getId(), savedCart.getItems().size());

            // Verify items were saved
            if (savedCart.getItems().size() > 0) {
                logger.info("Items after save:");
                for (CartItem item : savedCart.getItems()) {
                    logger.info("  - Item ID: {}, Product ID: {}, Quantity: {}", 
                        item.getId(), item.getProductId(), item.getQuantity());
                }
            } else {
                logger.warn("WARNING: Cart saved but has 0 items!");
            }

            // Convert to DTO
            CartResponse cartResponse = CartResponse.fromCart(savedCart);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item added to cart");
            response.put("cart", cartResponse);
            response.put("itemCount", cartResponse.getItemCount());

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error adding item to cart: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error adding item to cart: " + e.getMessage()));
        }
    }

    // Update item quantity
    @PutMapping("/items")
    @Transactional
    public ResponseEntity<?> updateItemQuantity(@Valid @RequestBody UpdateCartRequest updateRequest,
                                                 @AuthenticationPrincipal UserDetailsImpl currentUser) {
        logger.info("========== UPDATE QUANTITY ==========");
        logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "null");
        logger.info("Product ID: {}", updateRequest.getProductId());
        logger.info("Size: {}", updateRequest.getSize());
        logger.info("New Quantity: {}", updateRequest.getQuantity());
        
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }

            if (updateRequest.getQuantity() == null || updateRequest.getQuantity() < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse(false, "Valid quantity is required"));
            }

            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Cart cart = cartRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Cart not found"));

            logger.info("Cart ID: {}, Current items: {}", cart.getId(), cart.getItems().size());

            CartItem item = cart.getItems().stream()
                    .filter(i -> i.getProductId().equals(updateRequest.getProductId()) &&
                            (i.getSize() == null ? updateRequest.getSize() == null : 
                             i.getSize().equals(updateRequest.getSize())))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Item not found in cart"));

            if (updateRequest.getQuantity() == 0) {
                cart.getItems().remove(item);
                logger.info("Removed item from cart: {}", updateRequest.getProductId());
            } else {
                item.setQuantity(updateRequest.getQuantity());
                logger.info("Updated item quantity: {} -> {}", updateRequest.getProductId(), updateRequest.getQuantity());
            }

            Cart savedCart = cartRepository.save(cart);
            logger.info("Cart saved with {} items", savedCart.getItems().size());

            // Convert to DTO
            CartResponse cartResponse = CartResponse.fromCart(savedCart);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", updateRequest.getQuantity() == 0 ? "Item removed" : "Cart updated");
            response.put("cart", cartResponse);
            response.put("itemCount", cartResponse.getItemCount());

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating cart: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error updating cart: " + e.getMessage()));
        }
    }

    // Remove item from cart
    @DeleteMapping("/items")
    @Transactional
    public ResponseEntity<?> removeFromCart(@RequestBody CartItemRequest itemRequest,
                                             @AuthenticationPrincipal UserDetailsImpl currentUser) {
        logger.info("========== REMOVE FROM CART ==========");
        logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "null");
        logger.info("Product ID: {}", itemRequest.getProductId());
        logger.info("Size: {}", itemRequest.getSize());
        
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }

            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Cart cart = cartRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Cart not found"));

            boolean removed = cart.getItems().removeIf(item ->
                    item.getProductId().equals(itemRequest.getProductId()) &&
                    (item.getSize() == null ? itemRequest.getSize() == null : 
                     item.getSize().equals(itemRequest.getSize()))
            );

            if (!removed) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse(false, "Item not found in cart"));
            }

            Cart savedCart = cartRepository.save(cart);
            logger.info("Item removed. Cart now has {} items", savedCart.getItems().size());

            // Convert to DTO
            CartResponse cartResponse = CartResponse.fromCart(savedCart);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item removed from cart");
            response.put("cart", cartResponse);
            response.put("itemCount", cartResponse.getItemCount());

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error removing item from cart: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error removing item from cart: " + e.getMessage()));
        }
    }

    // Update entire cart 
    @PutMapping
    @Transactional
    public ResponseEntity<?> updateCart(@RequestBody Map<String, Object> updateRequest,
                                         @AuthenticationPrincipal UserDetailsImpl currentUser) {
        logger.info("========== UPDATE ENTIRE CART ==========");
        logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "null");
        
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }

            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Cart cart = cartRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Cart not found"));

            // Clear existing items
            cart.getItems().clear();

            // Add new items from request
            if (updateRequest.containsKey("items") && updateRequest.get("items") != null) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) updateRequest.get("items");
                logger.info("Updating cart with {} items", items.size());
                
                for (Map<String, Object> itemData : items) {
                    CartItem newItem = new CartItem();
                    newItem.setProductId(Long.parseLong(itemData.get("productId").toString()));
                    newItem.setProductName((String) itemData.get("productName"));
                    newItem.setPrice(Double.parseDouble(itemData.get("price").toString()));
                    newItem.setQuantity(Integer.parseInt(itemData.get("quantity").toString()));
                    newItem.setSize((String) itemData.get("size"));
                    newItem.setImageUrl((String) itemData.get("imageUrl"));
                    newItem.setCart(cart);
                    cart.getItems().add(newItem);
                }
            }

            Cart savedCart = cartRepository.save(cart);
            logger.info("Cart updated. Now has {} items", savedCart.getItems().size());

            // Convert to DTO
            CartResponse cartResponse = CartResponse.fromCart(savedCart);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cart updated successfully");
            response.put("cart", cartResponse);
            response.put("itemCount", cartResponse.getItemCount());

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating cart: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error updating cart: " + e.getMessage()));
        }
    }

    // Clear cart
    @DeleteMapping
    @Transactional
    public ResponseEntity<?> clearCart(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        logger.info("========== CLEAR CART ==========");
        logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "null");
        
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }

            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Cart cart = cartRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Cart not found"));

            int itemsCleared = cart.getItems().size();
            cart.getItems().clear();
            Cart savedCart = cartRepository.save(cart);
            
            logger.info("Cart cleared for user: {}. Removed {} items", user.getId(), itemsCleared);

            // Convert to DTO
            CartResponse cartResponse = CartResponse.fromCart(savedCart);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cart cleared");
            response.put("cart", cartResponse);
            response.put("itemCount", 0);

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error clearing cart: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error clearing cart: " + e.getMessage()));
        }
    }

    // Merge guest cart with user cart
    @PostMapping("/merge")
    @Transactional
    public ResponseEntity<?> mergeCart(@RequestBody MergeCartRequest mergeRequest,
                                        @AuthenticationPrincipal UserDetailsImpl currentUser) {
        logger.info("========== MERGE CART ==========");
        logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "null");
        logger.info("Items to merge: {}", mergeRequest.getItems() != null ? mergeRequest.getItems().size() : 0);
        
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "User not authenticated"));
            }

            if (mergeRequest.getItems() == null || mergeRequest.getItems().isEmpty()) {
                return ResponseEntity.ok(new ApiResponse(true, "No items to merge"));
            }

            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Cart cart = cartRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        Cart newCart = new Cart();
                        newCart.setUser(user);
                        return cartRepository.save(newCart);
                    });

            for (CartItemRequest guestItem : mergeRequest.getItems()) {
                Optional<CartItem> existingItem = cart.getItems().stream()
                        .filter(item -> item.getProductId().equals(guestItem.getProductId()) &&
                                (item.getSize() == null ? guestItem.getSize() == null : 
                                 item.getSize().equals(guestItem.getSize())))
                        .findFirst();

                if (existingItem.isPresent()) {
                    CartItem item = existingItem.get();
                    item.setQuantity(item.getQuantity() + guestItem.getQuantity());
                } else {
                    CartItem newItem = new CartItem();
                    newItem.setProductId(guestItem.getProductId());
                    newItem.setProductName(guestItem.getProductName());
                    newItem.setPrice(guestItem.getPrice());
                    newItem.setQuantity(guestItem.getQuantity() != null ? guestItem.getQuantity() : 1);
                    newItem.setSize(guestItem.getSize());
                    newItem.setImageUrl(guestItem.getImageUrl());
                    newItem.setCart(cart);
                    cart.getItems().add(newItem);
                }
            }

            Cart savedCart = cartRepository.save(cart);
            logger.info("Cart merged successfully. Now has {} items", savedCart.getItems().size());

            
            CartResponse cartResponse = CartResponse.fromCart(savedCart);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cart merged successfully");
            response.put("cart", cartResponse);
            response.put("itemCount", cartResponse.getItemCount());

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error merging cart: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error merging cart: " + e.getMessage()));
        }
    }

    // Get cart item count
    @GetMapping("/count")
    public ResponseEntity<?> getCartCount(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        logger.info("========== GET CART COUNT ==========");
        logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "null");
        
        try {
            if (currentUser == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("count", 0);
                return ResponseEntity.ok(response);
            }

            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Cart cart = cartRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        Cart newCart = new Cart();
                        newCart.setUser(user);
                        return cartRepository.save(newCart);
                    });

            int count = cart.getItems() != null ? 
                cart.getItems().stream().mapToInt(CartItem::getQuantity).sum() : 0;
            
            logger.info("Cart count for user {}: {}", user.getId(), count);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting cart count: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error getting cart count: " + e.getMessage()));
        }
    }
}