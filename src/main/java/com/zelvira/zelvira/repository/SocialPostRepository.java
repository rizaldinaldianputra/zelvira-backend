package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.SocialPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {
    List<SocialPost> findAllByOrderByCreatedAtDesc();

    List<SocialPost> findByStatusOrderByScheduledAtAsc(String status);

    List<SocialPost> findByStatusAndScheduledAtLessThanEqual(String status, LocalDateTime cutoff);

    @Query("SELECT p FROM SocialPost p WHERE " +
            "(p.scheduledAt IS NOT NULL AND p.scheduledAt BETWEEN :start AND :end) OR " +
            "(p.scheduledAt IS NULL AND p.publishedAt IS NOT NULL AND p.publishedAt BETWEEN :start AND :end) OR " +
            "(p.scheduledAt IS NULL AND p.publishedAt IS NULL AND p.createdAt IS NOT NULL AND p.createdAt BETWEEN :start AND :end) "
            +
            "ORDER BY COALESCE(p.scheduledAt, p.publishedAt, p.createdAt) ASC")
    List<SocialPost> findPostsBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByStatus(String status);

    long countByTargetPlatformsContaining(String platform);

    java.util.Optional<SocialPost> findFirstByTiktokPublishId(String tiktokPublishId);
}
