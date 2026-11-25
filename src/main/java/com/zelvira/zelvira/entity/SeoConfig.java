package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seo_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeoConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_name", unique = true, nullable = false, length = 50)
    private String pageName; // e.g., GLOBAL, HOME, PRODUCT_LIST, CHECKOUT

    @Column(name = "target_type", length = 20)
    @Builder.Default
    private String targetType = "HEAD"; // HEAD or BODY

    @Column(name = "meta_title", length = 255)
    private String metaTitle;

    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;

    @Column(name = "meta_keywords", length = 500)
    private String metaKeywords;

    @Column(name = "script_code", columnDefinition = "TEXT")
    private String scriptCode;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
