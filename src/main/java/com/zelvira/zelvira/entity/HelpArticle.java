package com.zelvira.zelvira.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "help_articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HelpArticle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String subtitle;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String category;

    private String imageUrl;

    @Column(name = "is_active")
    @Builder.Default
    @JsonProperty("isActive")
    private Boolean isActive = true;

    @Builder.Default
    private int displayOrder = 0;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean isActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        this.isActive = active;
    }
}
