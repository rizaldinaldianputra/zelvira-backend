package com.zelvira.zelvira.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "marketplace_order", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"marketplace_type", "marketplace_order_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_order_id")
    private Long internalOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "internal_order_id", insertable = false, updatable = false)
    @JsonIgnore
    private Order internalOrder;

    @Column(name = "marketplace_config_id")
    private Long marketplaceConfigId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marketplace_config_id", insertable = false, updatable = false)
    @JsonIgnore
    private MarketplaceConfig marketplaceConfig;

    @Column(name = "marketplace_type", nullable = false, length = 50)
    @Builder.Default
    private String marketplaceType = "TIKTOK";

    @Column(name = "marketplace_order_id", nullable = false, length = 100)
    private String marketplaceOrderId;

    @Column(name = "order_status", length = 50)
    private String orderStatus;

    @Column(name = "buyer_name", length = 255)
    private String buyerName;

    @Column(name = "buyer_email", length = 255)
    private String buyerEmail;

    @Column(name = "buyer_phone", length = 100)
    private String buyerPhone;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(length = 20)
    @Builder.Default
    private String currency = "IDR";

    @Column(name = "stock_deducted", nullable = false)
    @Builder.Default
    private Boolean stockDeducted = false;

    @Column(name = "stock_deducted_at")
    private LocalDateTime stockDeductedAt;

    @Column(name = "line_items_json", columnDefinition = "TEXT")
    private String lineItemsJson;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "order_created_at")
    private LocalDateTime orderCreatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
