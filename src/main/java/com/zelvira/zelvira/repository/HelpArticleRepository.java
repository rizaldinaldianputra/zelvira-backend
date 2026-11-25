package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.HelpArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HelpArticleRepository extends JpaRepository<HelpArticle, Long> {
    List<HelpArticle> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    List<HelpArticle> findAllByOrderByDisplayOrderAsc();

    @Query("SELECT COALESCE(MAX(h.displayOrder), 0) FROM HelpArticle h")
    Integer findMaxDisplayOrder();
}
