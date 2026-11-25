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
@Table(name = "pricing_formulas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingFormula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    // e.g. "MODAL + BIAYA_PACKING + BIAYA_OPERASIONAL + BIAYA_IKLAN + BIAYA_LAIN"
    @Column(name = "hpp_expression", columnDefinition = "TEXT", nullable = false)
    @Builder.Default
    private String hppExpression = "MODAL + BIAYA_PACKING + BIAYA_OPERASIONAL + BIAYA_IKLAN + BIAYA_LAIN";

    // e.g. "HPP / (1.0 - (ADMIN_FEE_PERCENT / 100.0))"
    @Column(name = "min_price_expression", columnDefinition = "TEXT", nullable = false)
    @Builder.Default
    private String minPriceExpression = "HPP / (1.0 - (ADMIN_FEE_PERCENT / 100.0))";

    // e.g. "(HPP + PROFIT_NOMINAL) / (1.0 - (ADMIN_FEE_PERCENT / 100.0) - (PROFIT_PERCENT / 100.0))"
    @Column(name = "selling_price_expression", columnDefinition = "TEXT", nullable = false)
    @Builder.Default
    private String sellingPriceExpression = "(HPP + PROFIT_NOMINAL) / (1.0 - (ADMIN_FEE_PERCENT / 100.0) - (PROFIT_PERCENT / 100.0))";

    // e.g. "HARGA_JUAL / (1.0 - (BUFFER_DISKON_PERCENT / 100.0))"
    @Column(name = "strike_price_expression", columnDefinition = "TEXT", nullable = false)
    @Builder.Default
    private String strikePriceExpression = "HARGA_JUAL / (1.0 - (BUFFER_DISKON_PERCENT / 100.0))";

    // Rounding: NONE, ROUND_UP_100, ROUND_UP_500, ROUND_UP_1000, CHARM_900
    @Column(name = "rounding_rule", length = 50, nullable = false)
    @Builder.Default
    private String roundingRule = "ROUND_UP_1000";

    @Column(name = "default_profit_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal defaultProfitPercent = new BigDecimal("30.00");

    @Column(name = "default_profit_nominal", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal defaultProfitNominal = BigDecimal.ZERO;

    @Column(name = "default_admin_fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal defaultAdminFeePercent = new BigDecimal("7.50");

    @Column(name = "default_buffer_diskon_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal defaultBufferDiskonPercent = new BigDecimal("20.00");

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

