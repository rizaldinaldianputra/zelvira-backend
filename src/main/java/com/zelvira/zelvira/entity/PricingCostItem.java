package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_cost_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingCostItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String category = "PACKAGING"; // PACKAGING, OPERATIONAL, MARKETING, OTHER

    @Column(name = "cost_type", nullable = false, length = 30)
    @Builder.Default
    private String costType = "FIXED_AMOUNT"; // FIXED_AMOUNT, PERCENTAGE

    @Column(name = "default_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal defaultAmount = BigDecimal.ZERO;

    @Column(length = 30)
    @Builder.Default
    private String unit = "pcs"; // pcs, lembar, paket, %

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

