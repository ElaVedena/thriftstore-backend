// dto/response/ReviewStatsResponse.java
package com.vedathrifts.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ReviewStatsResponse {
    private Double averageRating;
    private Integer totalReviews;
    private Map<Integer, Integer> ratingCounts;
}