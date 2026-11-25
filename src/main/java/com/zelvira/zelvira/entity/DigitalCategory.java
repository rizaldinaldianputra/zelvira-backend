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
@Table(name = "digital_categories", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "code", "is_postpaid" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigitalCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code; // e.g. "pulsa", "data", "pln", "emoney", "game", "voucher", "paket", "masa aktif", "bpjs", "pdam", "internet", "pasca", "gas", "finance"

    @Column(nullable = false, length = 100)
    private String name; // e.g. "Pulsa", "Paket Data", "Token PLN"

    @Column(name = "is_postpaid")
    @Builder.Default
    private Boolean isPostpaid = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "icon_name", length = 50)
    private String iconName;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "color_hex", length = 20)
    private String colorHex;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
