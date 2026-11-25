package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentSectionResponse {
    private Long id;
    private String platform;
    private String type;
    private String title;
    private String subtitle;
    private Integer displayOrder;
    private Boolean isActive;
    private java.time.LocalDateTime startDate;
    private java.time.LocalDateTime endDate;
    private java.time.LocalDateTime createdAt;
    private List<ContentItemResponse> items;
}
