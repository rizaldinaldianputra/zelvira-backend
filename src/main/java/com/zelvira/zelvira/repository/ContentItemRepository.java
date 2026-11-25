package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.ContentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {
    List<ContentItem> findByTypeAndPlatformAndIsActiveTrueOrderByCreatedAtDesc(String type, String platform);

    List<ContentItem> findByTypeAndPlatformOrderByCreatedAtDesc(String type, String platform);

    List<ContentItem> findAllByTypeOrderByCreatedAtDesc(String type);

    List<ContentItem> findByPlatformOrderByCreatedAtDesc(String platform);

    List<ContentItem> findAllByOrderByCreatedAtDesc();
}
