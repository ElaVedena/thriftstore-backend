package com.vedathrifts.controller;

import com.vedathrifts.dto.request.ProductRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.dto.response.OrderResponse;
import com.vedathrifts.model.Product;
import com.vedathrifts.model.Role;
import com.vedathrifts.model.User;
import com.vedathrifts.repository.OrderRepository;
import com.vedathrifts.repository.ProductRepository;
import com.vedathrifts.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = {"http://localhost:3000", "https://vedathrifts.com", "https://www.vedathrifts.com"}, allowCredentials = "true")
public class AdminController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDashboardStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            stats.put("totalProducts", productRepository.countActiveProducts());
            stats.put("totalUsers", userRepository.count());
            stats.put("totalOrders", orderRepository.count());
            
            Double totalRevenue = orderRepository.getTotalRevenue();
            stats.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
            
            List<Object[]> statusCounts = orderRepository.getOrderStatusCounts();
            Map<String, Long> statusCountMap = new HashMap<>();
            for (Object[] statusCount : statusCounts) {
                statusCountMap.put((String) statusCount[0], (Long) statusCount[1]);
            }
            stats.put("orderStatusCounts", statusCountMap);
            
            stats.put("pendingOrders", orderRepository.countByStatus("PENDING"));
            stats.put("processingOrders", orderRepository.countByStatus("PROCESSING"));
            stats.put("shippedOrders", orderRepository.countByStatus("SHIPPED"));
            stats.put("deliveredOrders", orderRepository.countByStatus("DELIVERED"));
            stats.put("cancelledOrders", orderRepository.countByStatus("CANCELLED"));
            stats.put("pendingPaymentOrders", orderRepository.countByStatus("PENDING_PAYMENT"));
            
            Pageable recentPageable = PageRequest.of(0, 5, Sort.by("createdAt").descending());
            Page<com.vedathrifts.model.Order> recentOrdersPage = orderRepository.findAll(recentPageable);
            
            List<OrderResponse> recentOrders = recentOrdersPage
                    .getContent()
                    .stream()
                    .map(OrderResponse::fromOrder) 
                    .collect(Collectors.toList());
            
            stats.put("recentOrders", recentOrders);
            
            Pageable lowStockPageable = PageRequest.of(0, 5);
            Page<Product> lowStockPage = productRepository.findByStockLessThanEqualActive(5, lowStockPageable);
            
            List<Map<String, Object>> lowStockProducts = lowStockPage
                    .getContent()
                    .stream()
                    .map(product -> {
                        Map<String, Object> productMap = new HashMap<>();
                        productMap.put("id", product.getId());
                        productMap.put("name", product.getName());
                        productMap.put("stock", product.getStock());
                        productMap.put("price", product.getPrice());
                        productMap.put("category", product.getCategory());
                        productMap.put("status", product.getStatus());
                        
                        if (product.getImages() != null && !product.getImages().isEmpty()) {
                            productMap.put("mainImage", product.getImages().get(0));
                        }
                        
                        return productMap;
                    })
                    .collect(Collectors.toList());
            
            stats.put("lowStock", lowStockProducts);
            stats.put("todayOrders", getTodayOrderCount());

            Pageable userPageable = PageRequest.of(0, 5, Sort.by("createdAt").descending());
            Page<User> recentUsersPage = userRepository.findAll(userPageable);
            
            List<Map<String, Object>> recentUsers = recentUsersPage
                    .getContent()
                    .stream()
                    .map(user -> {
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("id", user.getId());
                        userMap.put("name", user.getName());
                        userMap.put("email", user.getEmail());
                        userMap.put("role", user.getRole());
                        userMap.put("createdAt", user.getCreatedAt());
                        userMap.put("profileImage", user.getProfileImage());
                        return userMap;
                    })
                    .collect(Collectors.toList());
            
            stats.put("recentUsers", recentUsers);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Failed to load dashboard stats: ", e);
            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("error", "Failed to load complete dashboard stats");
            errorStats.put("message", e.getMessage());
            
             try {
                errorStats.put("totalProducts", productRepository.countActiveProducts());
                errorStats.put("totalUsers", userRepository.count());
                errorStats.put("totalOrders", orderRepository.count());
                
                Double revenue = orderRepository.getTotalRevenue();
                errorStats.put("totalRevenue", revenue != null ? revenue : 0.0);
                
            } catch (Exception ex) {
                errorStats.put("basicStatsFailed", ex.getMessage());
            }
            
            return ResponseEntity.status(500).body(errorStats);
        }
    }
    
    @GetMapping("/revenue/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getRevenueStats(
            @RequestParam(defaultValue = "today") String filter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            log.info("========== REVENUE STATS REQUEST ==========");
            log.info("Filter: {}, StartDate: {}, EndDate: {}", filter, startDate, endDate);
            
            Map<String, Object> stats = new HashMap<>();
            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime;
            
            if ("custom".equals(filter) && startDate != null && endDate != null) {
                LocalDate startLocalDate = LocalDate.parse(startDate);
                LocalDate endLocalDate = LocalDate.parse(endDate);
                startDateTime = startLocalDate.atStartOfDay();
                endDateTime = endLocalDate.atTime(23, 59, 59);
                log.info("Custom range: {} to {}", startDateTime, endDateTime);
            } else {
                switch(filter) {
                    case "today":
                        startDateTime = endDateTime.withHour(0).withMinute(0).withSecond(0);
                        log.info("Filter: Today - from {}", startDateTime);
                        break;
                    case "week":
                        startDateTime = endDateTime.minusDays(7);
                        log.info("Filter: Week - from {}", startDateTime);
                        break;
                    case "month":
                        startDateTime = endDateTime.minusMonths(1);
                        log.info("Filter: Month - from {}", startDateTime);
                        break;
                    default:
                        startDateTime = endDateTime.withHour(0).withMinute(0).withSecond(0);
                        log.info("Filter: Default - from {}", startDateTime);
                }
            }
            
            // Get ALL orders in date range (including ALL statuses for debugging)
            List<com.vedathrifts.model.Order> allOrders = orderRepository.findByDateRange(startDateTime, endDateTime);
            log.info("Total orders in date range: {}", allOrders.size());
            
            // Log status counts for debugging
            Map<String, Long> statusCounts = allOrders.stream()
                .collect(Collectors.groupingBy(com.vedathrifts.model.Order::getStatus, Collectors.counting()));
            log.info("Status counts: {}", statusCounts);
            
            // Include ALL orders for debugging purposes
            // This will show us what statuses your orders actually have
            List<com.vedathrifts.model.Order> paidOrders = allOrders;
            
            log.info("Orders included in revenue calculation (ALL): {}", paidOrders.size());
            
            // Calculate revenue from all orders
            Double totalRevenue = paidOrders.stream()
                    .mapToDouble(com.vedathrifts.model.Order::getTotal)
                    .sum();
            
            long paidOrderCount = paidOrders.size();
            
            log.info("Total Revenue (ALL ORDERS): {}, Order Count: {}", totalRevenue, paidOrderCount);
            
            // IMPORTANT: Return raw data directly, NOT wrapped in ApiResponse
            // This is what the frontend expects
            stats.put("totalRevenue", totalRevenue);
            stats.put("orderCount", paidOrderCount);
            stats.put("debugStatusCounts", statusCounts);
            
            // Build daily data
            List<Map<String, Object>> dailyData = new ArrayList<>();
            double maxRevenue = 0;
            
            LocalDate start = startDateTime.toLocalDate();
            LocalDate end = endDateTime.toLocalDate();
            long daysBetween = ChronoUnit.DAYS.between(start, end);
            
            if (daysBetween > 60) {
                daysBetween = 60;
            }
            
            // Group all orders by date
            Map<LocalDate, Double> dailyRevenueMap = allOrders.stream()
                .collect(Collectors.groupingBy(
                    order -> order.getCreatedAt().toLocalDate(),
                    Collectors.summingDouble(com.vedathrifts.model.Order::getTotal)
                ));
            
            for (int i = 0; i <= daysBetween; i++) {
                LocalDate currentDate = start.plusDays(i);
                Double dayRevenue = dailyRevenueMap.getOrDefault(currentDate, 0.0);
                
                if (dayRevenue > maxRevenue) maxRevenue = dayRevenue;
                
                Map<String, Object> dayData = new HashMap<>();
                dayData.put("label", currentDate.format(DateTimeFormatter.ofPattern("MMM dd")));
                dayData.put("total", dayRevenue);
                dailyData.add(dayData);
            }
            
            stats.put("dailyData", dailyData);
            stats.put("maxRevenue", maxRevenue);
            
            // Recent orders
            List<Map<String, Object>> recentOrdersList = allOrders.stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .limit(5)
                    .map(order -> {
                        Map<String, Object> orderMap = new HashMap<>();
                        orderMap.put("id", order.getId());
                        orderMap.put("orderNumber", order.getOrderNumber());
                        orderMap.put("total", order.getTotal());
                        orderMap.put("status", order.getStatus());
                        orderMap.put("createdAt", order.getCreatedAt());
                        if (order.getUser() != null) {
                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("name", order.getUser().getName());
                            userMap.put("email", order.getUser().getEmail());
                            orderMap.put("user", userMap);
                        }
                        return orderMap;
                    })
                    .collect(Collectors.toList());
            
            stats.put("recentOrders", recentOrdersList);
            stats.put("topProducts", new ArrayList<>());
            
            log.info("Returning revenue stats with totalRevenue: {}, orderCount: {}, statusCounts: {}", 
                totalRevenue, paidOrderCount, statusCounts);
            
            // Return raw stats directly (NOT wrapped in ApiResponse)
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Failed to get revenue stats: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("totalRevenue", 0);
            errorResponse.put("orderCount", 0);
            errorResponse.put("dailyData", new ArrayList<>());
            errorResponse.put("topProducts", new ArrayList<>());
            errorResponse.put("recentOrders", new ArrayList<>());
            errorResponse.put("maxRevenue", 0);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
   
    private long getTodayOrderCount() {
        try {
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            
            List<com.vedathrifts.model.Order> todayOrders = orderRepository.findByDateRange(startOfDay, endOfDay);
            return todayOrders.size();
        } catch (Exception e) {
            return 0;
        }
    }

    @GetMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<Product>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Boolean includeDeleted) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? Sort.by(sortBy).descending() 
                : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Product> products;
        
        if (includeDeleted != null && includeDeleted) {
            products = productRepository.findAll(pageable);
        } else {
            products = productRepository.findAllActive(pageable);
        }
        
        return ResponseEntity.ok(products);
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        try {
            Product product = new Product();
            
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
            product.setStatus("ACTIVE");
            product.setImages(productRequest.getImages() != null ? productRequest.getImages() : new ArrayList<>());
            product.setAvailableSizes(productRequest.getAvailableSizes() != null ? productRequest.getAvailableSizes() : new ArrayList<>());
            
            Product savedProduct = productRepository.save(product);
            
            return ResponseEntity.ok(new ApiResponse(true, "Product created successfully", savedProduct));
            
        } catch (Exception e) {
            log.error("Failed to create product: ", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to create product: " + e.getMessage()));
        }
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest productRequest) {
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
            
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
            product.setStatus(productRequest.getStatus() != null ? productRequest.getStatus() : "ACTIVE");
            
            if (productRequest.getImages() != null) {
                product.setImages(productRequest.getImages());
            }
            
            if (productRequest.getAvailableSizes() != null) {
                product.setAvailableSizes(productRequest.getAvailableSizes());
            }
            
            Product updatedProduct = productRepository.save(product);
            
            return ResponseEntity.ok(new ApiResponse(true, "Product updated successfully", updatedProduct));
            
        } catch (Exception e) {
            log.error("Failed to update product: ", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to update product: " + e.getMessage()));
        }
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
            
            product.setStatus("DELETED");
            productRepository.save(product);
            
            return ResponseEntity.ok(new ApiResponse(true, "Product deleted successfully"));
            
        } catch (Exception e) {
            log.error("Failed to delete product: ", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to delete product: " + e.getMessage()));
        }
    }

    @GetMapping("/products/low-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<Product>> getLowStockProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productRepository.findByStockLessThanEqualActive(5, pageable);
        
        return ResponseEntity.ok(products);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<com.vedathrifts.model.Order> ordersPage;
            
            if (status != null && !status.isEmpty()) {
                ordersPage = orderRepository.findByStatusWithUser(status.toUpperCase(), pageable);
            } else {
                ordersPage = orderRepository.findAllWithUser(pageable);
            }
            
            Page<OrderResponse> orderResponses = ordersPage.map(OrderResponse::fromOrder);
            
            return ResponseEntity.ok(orderResponses);
            
        } catch (Exception e) {
            log.error("Failed to fetch orders: ", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to fetch orders: " + e.getMessage()));
        }
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getOrderById(@PathVariable Long orderId) {
        try {
            com.vedathrifts.model.Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
            
            return ResponseEntity.ok(OrderResponse.fromOrder(order));
            
        } catch (Exception e) {
            log.error("Failed to fetch order: ", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to fetch order: " + e.getMessage()));
        }
    }

    @PutMapping("/orders/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long orderId, @RequestParam String status) {
        try {
            com.vedathrifts.model.Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
            
            order.setStatus(status.toUpperCase());
            orderRepository.save(order);
            
            return ResponseEntity.ok(new ApiResponse(true, "Order status updated to " + status));
            
        } catch (Exception e) {
            log.error("Failed to update order status: ", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to update order status: " + e.getMessage()));
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<User> users;
            
            if (search != null && !search.isEmpty()) {
                users = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable);
            } else if (role != null && !role.isEmpty()) {
                users = userRepository.findByRole(Role.valueOf(role.toUpperCase()), pageable);
            } else {
                users = userRepository.findAll(pageable);
            }
            
            List<Map<String, Object>> safeUsers = users
                    .getContent()
                    .stream()
                    .map(user -> {
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("id", user.getId());
                        userMap.put("name", user.getName());
                        userMap.put("email", user.getEmail());
                        userMap.put("phone", user.getPhone());
                        userMap.put("role", user.getRole());
                        userMap.put("profileImage", user.getProfileImage());
                        userMap.put("createdAt", user.getCreatedAt());
                        
                        Long orderCount = orderRepository.countByUserId(user.getId());
                        userMap.put("orderCount", orderCount);
                        
                        return userMap;
                    })
                    .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", safeUsers);
            response.put("totalElements", users.getTotalElements());
            response.put("totalPages", users.getTotalPages());
            response.put("size", users.getSize());
            response.put("number", users.getNumber());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to fetch users: ", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to fetch users: " + e.getMessage()));
        }
    }

    @PutMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long userId, @RequestParam String role) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
            
            Role newRole = Role.valueOf(role.toUpperCase());
            Role currentRole = user.getRole();
            
            // Check if trying to create a second admin
            if (newRole == Role.ADMIN && currentRole != Role.ADMIN) {
                boolean adminExists = userRepository.existsAdmin();
                if (adminExists) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "Only one admin user is allowed"));
                }
            }
            
            // Check if trying to demote the only admin
            if (currentRole == Role.ADMIN && newRole != Role.ADMIN) {
                long adminCount = userRepository.countByRole(Role.ADMIN);
                if (adminCount <= 1) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "Cannot demote the only admin user"));
                }
            }
            
            user.setRole(newRole);
            userRepository.save(user);
            
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("name", user.getName());
            userMap.put("email", user.getEmail());
            userMap.put("role", user.getRole());
            
            return ResponseEntity.ok(new ApiResponse(true, "User role updated to " + role, userMap));
            
        } catch (Exception e) {
            log.error("Failed to update user role: ", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to update user role: " + e.getMessage()));
        }
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
            
            // Check if trying to delete the only admin
            if (user.getRole() == Role.ADMIN) {
                long adminCount = userRepository.countByRole(Role.ADMIN);
                if (adminCount <= 1) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "Cannot delete the only admin user"));
                }
            }
            
            Pageable pageable = PageRequest.of(0, 1);
            Page<com.vedathrifts.model.Order> userOrders = orderRepository.findByUserId(userId, pageable);
            
            if (userOrders.hasContent()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Cannot delete user with existing orders. Consider deactivating instead."));
            }
            
            userRepository.delete(user);
            
            return ResponseEntity.ok(new ApiResponse(true, "User deleted successfully"));
            
        } catch (Exception e) {
            log.error("Failed to delete user: ", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to delete user: " + e.getMessage()));
        }
    }
}