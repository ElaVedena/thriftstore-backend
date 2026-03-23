package com.vedathrifts.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "reviews", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
public class Review {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @Column(nullable = false)
    private Integer rating;
    
    @Column(length = 100)
    private String title;
    
    @Column(length = 1000, nullable = false)
    private String comment;
    
    @ElementCollection
    @CollectionTable(name = "review_images", 
                     joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "image_url", length = 500)
    private List<String> images = new ArrayList<>();
    
    @Column(nullable = false)
    private Boolean verified = false;
    
    @Column(nullable = false)
    private Integer helpful = 0;
    
    @Column(nullable = false)
    private Integer notHelpful = 0;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public void setVerified(boolean hasPurchased) {
        this.verified = hasPurchased;
    }
}