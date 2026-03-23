package com.vedathrifts.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String orderNumber;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    
    @Column(nullable = false)
    private Double subtotal;
    
    private Double shippingCost;
    
    @Column(nullable = false)
    private Double total;
    
    private String status; // PENDING_PAYMENT, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED, PAYMENT_FAILED
    
    private String paymentMethod; // M-PESA
    
    private String paymentCode;
    
    private String shippingAddress;
    
    private String city;
    
    private String county;
    
    private String phone;

    private String mpesaReceiptNumber;
    private String checkoutRequestId;
    private String paymentFailureReason;
    private String mpesaTransactionDate;
    private String paymentResult;
  
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}