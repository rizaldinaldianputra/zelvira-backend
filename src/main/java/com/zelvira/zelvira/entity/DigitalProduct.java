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
@Table(name = "digital_products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigitalProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "digital_category_id")
    private Long digitalCategoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "digital_category_id", insertable = false, updatable = false)
    @JsonIgnore
    private DigitalCategory digitalCategory;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(length = 50)
    private String category;

    @Column(length = 50)
    private String brand;

    @Column(length = 50)
    private String type; // e.g. Umum

    @Column(name = "seller_name", length = 100)
    private String sellerName;

    @Column(name = "buyer_sku_code", nullable = false, unique = true, length = 50)
    private String buyerSkuCode;

    // Harga Beli dari Digiflazz
    @Column(nullable = false)
    private Double price;

    // Harga Jual kita ke User (bisa diset secara manual/otomatis)
    @Column(name = "selling_price", nullable = false)
    private Double sellingPrice;

    @Column(name = "buyer_product_status")
    @Builder.Default
    private Boolean buyerProductStatus = true;

    @Column(name = "seller_product_status")
    @Builder.Default
    private Boolean sellerProductStatus = true;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "unlimited_stock")
    @Builder.Default
    private Boolean unlimitedStock = true;

    private Integer stock;

    private Boolean multi;

    @Column(name = "start_cut_off", length = 10)
    private String startCutOff;

    @Column(name = "end_cut_off", length = 10)
    private String endCutOff;

    @Column(name = "description", length = 500)
    private String desc;

    // Khusus Postpaid
    private Double admin;
    private Double commission;

    @Column(name = "is_postpaid")
    @Builder.Default
    private Boolean isPostpaid = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
