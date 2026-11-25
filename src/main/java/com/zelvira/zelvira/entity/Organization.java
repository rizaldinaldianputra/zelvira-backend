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
@Table(name = "organization")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String instagramUrl;

    @Column(length = 500)
    private String facebookUrl;

    @Column(length = 500)
    private String twitterUrl;

    @Column(length = 500)
    private String youtubeUrl;

    @Column(length = 500)
    private String tiktokUrl;

    @Column(length = 500)
    private String playstoreUrl;

    @Column(length = 500)
    private String appstoreUrl;

    private Double latitude;
    private Double longitude;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
