// dto/response/ReviewResponse.java
package com.vedathrifts.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReviewResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userAvatar;
    private Long productId;
    private Integer rating;
    private String title;
    private String comment;
    private List<String> images;
    private Boolean verified;
    private Integer helpful;
    private Integer notHelpful;
    private LocalDateTime date;
    private LocalDateTime createdAt;
    
    public static ReviewResponse fromReview(com.vedathrifts.model.Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUser().getId())
                .userName(review.getUser().getName())
                .userAvatar(review.getUser().getProfileImage())
                .productId(review.getProduct().getId())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .images(review.getImages())
                .verified(review.getVerified())
                .helpful(review.getHelpful())
                .notHelpful(review.getNotHelpful())
                .date(review.getCreatedAt())
                .createdAt(review.getCreatedAt())
                .build();
    }
}