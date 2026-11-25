package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.SeoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeoConfigRepository extends JpaRepository<SeoConfig, Long> {
    Optional<SeoConfig> findByPageName(String pageName);
}
