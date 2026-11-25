package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByPlatformAndAccountId(String platform, String accountId);

    Optional<SocialAccount> findFirstByPlatformAndIsActiveTrue(String platform);

    List<SocialAccount> findAllByPlatform(String platform);

    List<SocialAccount> findAllByIsActiveTrue();
}
