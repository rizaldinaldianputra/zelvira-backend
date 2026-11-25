package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeoConfigRequest {
    private String pageName;
    private String targetType;
    private String metaTitle;
    private String metaDescription;
    private String metaKeywords;
    private String scriptCode;
    private Boolean isActive;
}
