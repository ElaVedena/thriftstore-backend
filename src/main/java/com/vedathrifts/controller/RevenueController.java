// src/main/java/com/vedathrifts/controller/RevenueController.java
package com.vedathrifts.controller;

import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.model.Order;
import com.vedathrifts.model.OrderItem;
import com.vedathrifts.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin/revenue")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "https://vedathrifts.com", "https://www.vedathrifts.com"}, allowCredentials = "true")
public class RevenueController {

    private final OrderRepository orderRepository;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getRevenueStats(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        try {
            log.info("========== REVENUE STATS REQUEST ==========");
            log.info("Filter: {}, StartDate: {}, EndDate: {}", filter, startDate, endDate);
            
            // Get ALL orders first (don't filter by status yet)
            List<Order> allOrders = orderRepository.findAll();
            log.info("Total orders in system: {}", allOrders.size());
            
            // Log order statuses for debugging
            Map<String, Long> statusCounts = allOrders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
            log.info("Order status counts: {}", statusCounts);
            
            // Get PAID orders (including COMPLETED and SUCCESS)
            List<Order> paidOrders = allOrders.stream()
                .filter(order -> {
                    String status = order.getStatus();
                    return "PAID".equals(status) || 
                           "COMPLETED".equals(status) || 
                           "SUCCESS".equals(status) ||
                           "DELIVERED".equals(status);
                })
                .collect(Collectors.toList());
            
            log.info("Found {} PAID/COMPLETED orders", paidOrders.size());
            
            // Apply date filtering
            List<Order> filteredOrders = paidOrders;
            
            if (startDate != null && endDate != null) {
                // Custom date range
                LocalDateTime startDateTime = startDate.atStartOfDay();
                LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
                
                filteredOrders = paidOrders.stream()
                    .filter(order -> {
                        LocalDateTime createdAt = order.getCreatedAt();
                        return createdAt != null && 
                               !createdAt.isBefore(startDateTime) && 
                               !createdAt.isAfter(endDateTime);
                    })
                    .collect(Collectors.toList());
                log.info("Filtered by custom date range: {} orders", filteredOrders.size());
                
            } else if (filter != null && !filter.isEmpty() && !"all".equals(filter)) {
                // Apply predefined filter
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startDateTime = null;
                
                switch(filter) {
                    case "today":
                        startDateTime = now.toLocalDate().atStartOfDay();
                        log.info("Filter: Today");
                        break;
                    case "week":
                        startDateTime = now.toLocalDate().atStartOfDay().minusDays(7);
                        log.info("Filter: This Week");
                        break;
                    case "month":
                        startDateTime = now.toLocalDate().atStartOfDay().minusDays(30);
                        log.info("Filter: This Month");
                        break;
                    default:
                        log.info("Filter: All time");
                        break;
                }
                
                if (startDateTime != null) {
                    final LocalDateTime finalStart = startDateTime;
                    filteredOrders = paidOrders.stream()
                        .filter(order -> {
                            LocalDateTime createdAt = order.getCreatedAt();
                            return createdAt != null && !createdAt.isBefore(finalStart);
                        })
                        .collect(Collectors.toList());
                    log.info("Filtered by {}: {} orders", filter, filteredOrders.size());
                }
            }
            
            // Calculate revenue
            double totalRevenue = filteredOrders.stream()
                .mapToDouble(Order::getTotal)
                .sum();
            
            int orderCount = filteredOrders.size();
            
            log.info("✅ Total Revenue: {}, Order Count: {}", totalRevenue, orderCount);
            
            // Build daily breakdown
            List<Map<String, Object>> dailyData = new ArrayList<>();
            
            if (!filteredOrders.isEmpty()) {
                // Group orders by date
                Map<String, Double> dailyRevenue = new LinkedHashMap<>();
                
                for (Order order : filteredOrders) {
                    if (order.getCreatedAt() != null) {
                        String dateKey = order.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd"));
                        dailyRevenue.merge(dateKey, order.getTotal(), Double::sum);
                    }
                }
                
                // Convert to list and sort by date
                dailyRevenue.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        Map<String, Object> dayData = new HashMap<>();
                        dayData.put("label", entry.getKey());
                        dayData.put("total", entry.getValue());
                        dailyData.add(dayData);
                    });
            }
            
            // Calculate max daily revenue for chart
            double maxRevenue = dailyData.stream()
                .mapToDouble(d -> (double) d.get("total"))
                .max()
                .orElse(0);
            
            // Get recent orders (last 5)
            List<Order> recentOrders = filteredOrders.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .limit(5)
                .collect(Collectors.toList());
            
            // Build product revenue data
            Map<String, ProductRevenue> productRevenueMap = new HashMap<>();
            
            for (Order order : filteredOrders) {
                if (order.getItems() != null) {
                    for (OrderItem item : order.getItems()) {
                        String productName = item.getProductName() != null ? item.getProductName() : "Unknown Product";
                        ProductRevenue pr = productRevenueMap.getOrDefault(productName, new ProductRevenue(productName));
                        pr.quantity += item.getQuantity();
                        pr.revenue += item.getPrice() * item.getQuantity();
                        productRevenueMap.put(productName, pr);
                    }
                }
            }
            
            // Get top 5 products by revenue
            List<Map<String, Object>> topProducts = productRevenueMap.values().stream()
                .sorted((a, b) -> Double.compare(b.revenue, a.revenue))
                .limit(5)
                .map(pr -> {
                    Map<String, Object> productData = new HashMap<>();
                    productData.put("name", pr.name);
                    productData.put("quantity", pr.quantity);
                    productData.put("revenue", pr.revenue);
                    return productData;
                })
                .collect(Collectors.toList());
            
            // Build response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("totalRevenue", totalRevenue);
            responseData.put("orderCount", orderCount);
            responseData.put("dailyData", dailyData);
            responseData.put("recentOrders", recentOrders);
            responseData.put("topProducts", topProducts);
            responseData.put("maxRevenue", maxRevenue);
            
            log.info("Response: totalRevenue={}, orderCount={}", totalRevenue, orderCount);
            
            return ResponseEntity.ok(new ApiResponse(true, "Revenue stats retrieved", responseData));
            
        } catch (Exception e) {
            log.error("❌ Failed to get revenue stats: ", e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to get revenue stats: " + e.getMessage()));
        }
    }
    
    // Inner class for product revenue aggregation
    private static class ProductRevenue {
        String name;
        int quantity;
        double revenue;
        
        ProductRevenue(String name) {
            this.name = name;
            this.quantity = 0;
            this.revenue = 0;
        }
    }
}