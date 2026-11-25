package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.MobileAppSettingDTO;
import com.zelvira.zelvira.entity.MobileAppSetting;
import com.zelvira.zelvira.repository.MobileAppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobileAppSettingService {

    private final MobileAppSettingRepository repository;

    public MobileAppSettingService(MobileAppSettingRepository repository) {
        this.repository = repository;
    }

    public MobileAppSetting getSettings() {
        return repository.findFirstByOrderByIdAsc()
                .orElseGet(() -> repository.save(MobileAppSetting.builder()
                        .latestVersion("1.0.0")
                        .minVersion("1.0.0")
                        .title("Pembaruan Aplikasi Tersedia")
                        .message(
                                "Versi terbaru aplikasi Zelvira telah tersedia. Dapatkan fitur terbaru dan pengalaman berbelanja yang lebih baik.")
                        .downloadUrl("https://play.google.com/store/apps/details?id=com.zelvira.ecommerce")
                        .forceUpdate(false)
                        .build()));
    }

    @Transactional
    public MobileAppSetting updateSettings(MobileAppSettingDTO dto) {
        MobileAppSetting setting = repository.findFirstByOrderByIdAsc()
                .orElse(new MobileAppSetting());

        if (dto.getLatestVersion() != null && !dto.getLatestVersion().trim().isEmpty()) {
            setting.setLatestVersion(dto.getLatestVersion().trim());
        }
        if (dto.getMinVersion() != null && !dto.getMinVersion().trim().isEmpty()) {
            setting.setMinVersion(dto.getMinVersion().trim());
        }
        if (dto.getTitle() != null) {
            setting.setTitle(dto.getTitle());
        }
        if (dto.getMessage() != null) {
            setting.setMessage(dto.getMessage());
        }
        if (dto.getDownloadUrl() != null) {
            setting.setDownloadUrl(dto.getDownloadUrl());
        }
        if (dto.getForceUpdate() != null) {
            setting.setForceUpdate(dto.getForceUpdate());
        }

        return repository.save(setting);
    }
}
