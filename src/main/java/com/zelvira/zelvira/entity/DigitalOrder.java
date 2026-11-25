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
@Table(name = "digital_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigitalOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", unique = true, nullable = false, length = 60)
    private String orderNumber;

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    @Column(name = "digital_product_id")
    private Long digitalProductId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "digital_product_id", insertable = false, updatable = false)
    @JsonIgnore
    private DigitalProduct digitalProduct;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "customer_no", nullable = false, length = 100)
    private String customerNo;

    @Column(name = "sku_code", nullable = false, length = 100)
    private String skuCode;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "price", nullable = false)
    private Double price;

    @Column(name = "payment_status", nullable = false, length = 30)
    @Builder.Default
    private String paymentStatus = "PENDING"; // PENDING, PAID, FAILED, EXPIRED

    @Column(name = "payment_token", length = 255)
    private String paymentToken;

    @Column(name = "payment_url", length = 500)
    private String paymentUrl;

    @Column(name = "digiflazz_status", length = 30)
    @Builder.Default
    private String digiflazzStatus = "PENDING"; // PENDING, PROCESSING, SUCCESS, FAILED

    @Column(name = "digiflazz_sn", length = 255)
    private String digiflazzSn;

    @Column(name = "digiflazz_message", columnDefinition = "TEXT")
    private String digiflazzMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
