package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.ContentSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentSectionRepository extends JpaRepository<ContentSection, Long> {
    List<ContentSection> findByPlatformOrderByDisplayOrderAsc(String platform);

    List<ContentSection> findByPlatformAndType(String platform, String type);
}
