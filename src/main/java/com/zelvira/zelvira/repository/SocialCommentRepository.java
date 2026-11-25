package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.SocialComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialCommentRepository extends JpaRepository<SocialComment, Long> {
    List<SocialComment> findAllByOrderByCreatedAtDesc();

    List<SocialComment> findByPlatformOrderByCreatedAtDesc(String platform);

    List<SocialComment> findByPostIdOrderByCreatedAtDesc(Long postId);

    Optional<SocialComment> findByExternalCommentId(String externalCommentId);

    boolean existsByExternalCommentId(String externalCommentId);

    long countByIsRepliedFalse();
}
