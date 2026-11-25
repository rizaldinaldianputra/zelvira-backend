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
@Table(name = "product_pricing_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPricingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @Column(name = "variant_id")
    private Long variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", insertable = false, updatable = false)
    private ProductVariant variant;

    @Column(name = "formula_id")
    private Long formulaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formula_id", insertable = false, updatable = false)
    private PricingFormula formula;

    // JSON map of cost item codes and amounts used for this product: e.g. {"PLASTIK": 1000, "HANGTAG": 500}
    @Column(name = "cost_overrides_json", columnDefinition = "TEXT")
    private String costOverridesJson;

    @Column(name = "base_modal", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal baseModal = BigDecimal.ZERO;

    @Column(name = "calculated_hpp", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedHpp = BigDecimal.ZERO;

    @Column(name = "calculated_min_price", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedMinPrice = BigDecimal.ZERO;

    @Column(name = "calculated_selling_price", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedSellingPrice = BigDecimal.ZERO;

    @Column(name = "calculated_strike_price", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedStrikePrice = BigDecimal.ZERO;

    @Column(name = "target_profit_nominal", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal targetProfitNominal = BigDecimal.ZERO;

    @Column(name = "target_profit_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal targetProfitPercent = BigDecimal.ZERO;

    @Column(name = "admin_fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal adminFeePercent = BigDecimal.ZERO;

    @Column(name = "buffer_diskon_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal bufferDiskonPercent = BigDecimal.ZERO;

    @Column(name = "is_applied")
    @Builder.Default
    private Boolean isApplied = false;

    @Column(name = "last_applied_at")
    private LocalDateTime lastAppliedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

