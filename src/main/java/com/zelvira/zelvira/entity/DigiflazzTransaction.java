package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "digiflazz_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigiflazzTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "ref_id", nullable = false, unique = true, length = 100)
    private String refId;

    @Column(name = "customer_no", nullable = false, length = 50)
    private String customerNo;

    @Column(name = "buyer_sku_code", nullable = false, length = 50)
    private String buyerSkuCode;

    @Column(name = "message", length = 255)
    private String message;

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "Pending"; // Sukses, Pending, Gagal

    @Column(name = "rc", length = 10)
    private String rc;

    @Column(name = "sn", length = 100)
    private String sn;

    @Column(name = "buyer_last_saldo")
    private Double buyerLastSaldo;

    @Column(name = "price")
    private Double price;

    @Column(name = "selling_price")
    private Double sellingPrice; // For postpaid

    @Column(name = "admin")
    private Double admin; // Admin fee for postpaid

    @Column(name = "transaction_type", length = 20)
    @Builder.Default
    private String transactionType = "PREPAID"; // PREPAID, POSTPAID

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
