package com.vedathrifts.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @Column(nullable = false)
    private String productName;
    
    @Column(nullable = false)
    private Double price;
    
    @Column(nullable = false)
    private Integer quantity;
    
    private String size;
    
    private String imageUrl;
   
    public Double getSubtotal() {
        return price * quantity;
    }
    
    public void updateProductStock() {
        if (product != null) {
            int newStock = product.getStock() - quantity;
            product.setStock(Math.max(0, newStock));
            
            if (newStock <= 0) {
                product.setStatus("OUT_OF_STOCK");
            }
        }
    }
}