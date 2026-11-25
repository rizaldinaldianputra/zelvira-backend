package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleResponse {
    private Long id;
    private String name;
    private String platform;
    private String bannerUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean isActive;
    private Integer maxPurchasePerUser;
    private List<FlashSaleItemResponse> items;
    private LocalDateTime createdAt;
}
