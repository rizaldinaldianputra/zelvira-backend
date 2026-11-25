package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.MobileAppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MobileAppSettingRepository extends JpaRepository<MobileAppSetting, Long> {
    Optional<MobileAppSetting> findFirstByOrderByIdAsc();
}
