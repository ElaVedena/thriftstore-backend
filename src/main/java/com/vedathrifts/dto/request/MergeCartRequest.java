package com.vedathrifts.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class MergeCartRequest {
    private List<CartItemRequest> items;
}