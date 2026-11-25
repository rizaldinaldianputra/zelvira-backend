package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String type; // TOPUP, PAYMENT, POINTS_EARNED, POINTS_REDEEMED

    @Column(nullable = false)
    @Builder.Default
    private Double amount = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Long points = 0L;

    @Column(length = 255)
    private String description;

    @Column(name = "reference_id", length = 100)
    private String referenceId; // e.g. order number or midtrans transaction id

    @Column(name = "snap_token", length = 255)
    private String snapToken;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, SUCCESS, FAILED

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
