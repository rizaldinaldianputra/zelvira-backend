package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    List<NotificationHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT n FROM NotificationHistory n WHERE n.userId = :userId OR n.userId IS NULL ORDER BY n.createdAt DESC")
    List<NotificationHistory> findUserAndGlobalNotifications(@Param("userId") Long userId);

    Long countByUserIdAndIsReadFalse(Long userId);

    List<NotificationHistory> findByUserIdAndIsReadFalse(Long userId);

    void deleteByUserId(Long userId);

    // For news (global)
    List<NotificationHistory> findByUserIdIsNullOrderByCreatedAtDesc();
}
