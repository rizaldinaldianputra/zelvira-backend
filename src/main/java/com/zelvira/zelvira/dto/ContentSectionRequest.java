package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentSectionRequest {
    private String platform;
    private String type;
    private String title;
    private String subtitle;
    private Integer displayOrder;
    private Boolean isActive;
    private java.time.LocalDateTime startDate;
    private java.time.LocalDateTime endDate;
}
