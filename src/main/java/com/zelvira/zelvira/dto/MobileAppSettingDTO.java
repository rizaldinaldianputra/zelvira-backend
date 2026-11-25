package com.zelvira.zelvira.dto;

public class MobileAppSettingDTO {
    private String latestVersion;
    private String minVersion;
    private String title;
    private String message;
    private String downloadUrl;
    private Boolean forceUpdate;

    public MobileAppSettingDTO() {
    }

    public MobileAppSettingDTO(String latestVersion, String minVersion, String title, String message,
            String downloadUrl, Boolean forceUpdate) {
        this.latestVersion = latestVersion;
        this.minVersion = minVersion;
        this.title = title;
        this.message = message;
        this.downloadUrl = downloadUrl;
        this.forceUpdate = forceUpdate;
    }

    public static MobileAppSettingDTOBuilder builder() {
        return new MobileAppSettingDTOBuilder();
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

    public static class MobileAppSettingDTOBuilder {
        private String latestVersion;
        private String minVersion;
        private String title;
        private String message;
        private String downloadUrl;
        private Boolean forceUpdate;

        MobileAppSettingDTOBuilder() {
        }

        public MobileAppSettingDTOBuilder latestVersion(String latestVersion) {
            this.latestVersion = latestVersion;
            return this;
        }

        public MobileAppSettingDTOBuilder minVersion(String minVersion) {
            this.minVersion = minVersion;
            return this;
        }

        public MobileAppSettingDTOBuilder title(String title) {
            this.title = title;
            return this;
        }

        public MobileAppSettingDTOBuilder message(String message) {
            this.message = message;
            return this;
        }

        public MobileAppSettingDTOBuilder downloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
            return this;
        }

        public MobileAppSettingDTOBuilder forceUpdate(Boolean forceUpdate) {
            this.forceUpdate = forceUpdate;
            return this;
        }

        public MobileAppSettingDTO build() {
            return new MobileAppSettingDTO(latestVersion, minVersion, title, message, downloadUrl, forceUpdate);
        }
    }
}
