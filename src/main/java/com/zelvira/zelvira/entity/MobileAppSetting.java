package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "mobile_settings")
public class MobileAppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "latest_version", nullable = false, length = 50)
    private String latestVersion = "1.0.0";

    @Column(name = "min_version", nullable = false, length = 50)
    private String minVersion = "1.0.0";

    @Column(length = 255)
    private String title = "Pembaruan Aplikasi Tersedia";

    @Column(columnDefinition = "TEXT")
    private String message = "Versi terbaru aplikasi Zelvira telah tersedia. Dapatkan fitur terbaru dan pengalaman berbelanja yang lebih baik.";

    @Column(name = "download_url", length = 500)
    private String downloadUrl = "https://play.google.com/store/apps/details?id=com.zelvira.ecommerce";

    @Column(name = "force_update", nullable = false)
    private Boolean forceUpdate = false;

    @Column(name = "digital_product_enabled")
    private Boolean digitalProductEnabled = true;

    @Column(name = "digital_product_title", length = 100)
    private String digitalProductTitle = "Digital Produk";

    @Column(name = "digital_product_icon_url", length = 500)
    private String digitalProductIconUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public MobileAppSetting() {
    }

    public MobileAppSetting(Long id, String latestVersion, String minVersion, String title, String message,
            String downloadUrl, Boolean forceUpdate, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.latestVersion = latestVersion;
        this.minVersion = minVersion;
        this.title = title;
        this.message = message;
        this.downloadUrl = downloadUrl;
        this.forceUpdate = forceUpdate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MobileAppSettingBuilder builder() {
        return new MobileAppSettingBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = minVersion;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Boolean getForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(Boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public Boolean getDigitalProductEnabled() {
        return digitalProductEnabled != null ? digitalProductEnabled : true;
    }

    public void setDigitalProductEnabled(Boolean digitalProductEnabled) {
        this.digitalProductEnabled = digitalProductEnabled;
    }

    public String getDigitalProductTitle() {
        return digitalProductTitle != null ? digitalProductTitle : "Digital Produk";
    }

    public void setDigitalProductTitle(String digitalProductTitle) {
        this.digitalProductTitle = digitalProductTitle;
    }

    public String getDigitalProductIconUrl() {
        return digitalProductIconUrl;
    }

    public void setDigitalProductIconUrl(String digitalProductIconUrl) {
        this.digitalProductIconUrl = digitalProductIconUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class MobileAppSettingBuilder {
        private Long id;
        private String latestVersion = "1.0.0";
        private String minVersion = "1.0.0";
        private String title = "Pembaruan Aplikasi Tersedia";
        private String message = "Versi terbaru aplikasi Zelvira telah tersedia. Dapatkan fitur terbaru dan pengalaman berbelanja yang lebih baik.";
        private String downloadUrl = "https://play.google.com/store/apps/details?id=com.zelvira.ecommerce";
        private Boolean forceUpdate = false;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        MobileAppSettingBuilder() {
        }

        public MobileAppSettingBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public MobileAppSettingBuilder latestVersion(String latestVersion) {
            this.latestVersion = latestVersion;
            return this;
        }

        public MobileAppSettingBuilder minVersion(String minVersion) {
            this.minVersion = minVersion;
            return this;
        }

        public MobileAppSettingBuilder title(String title) {
            this.title = title;
            return this;
        }

        public MobileAppSettingBuilder message(String message) {
            this.message = message;
            return this;
        }

        public MobileAppSettingBuilder downloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
            return this;
        }

        public MobileAppSettingBuilder forceUpdate(Boolean forceUpdate) {
            this.forceUpdate = forceUpdate;
            return this;
        }

        public MobileAppSettingBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public MobileAppSettingBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public MobileAppSetting build() {
            return new MobileAppSetting(id, latestVersion, minVersion, title, message, downloadUrl, forceUpdate,
                    createdAt, updatedAt);
        }
    }
}
