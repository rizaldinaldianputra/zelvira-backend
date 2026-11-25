package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.PrivacyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivacyPolicyRepository extends JpaRepository<PrivacyPolicy, Long> {
    Optional<PrivacyPolicy> findFirstByIsActiveTrueOrderByUpdatedAtDesc();

    Optional<PrivacyPolicy> findFirstByOrderByUpdatedAtDesc();

    Optional<PrivacyPolicy> findFirstByOrderByIdAsc();

    List<PrivacyPolicy> findAllByIsActiveTrueOrderByUpdatedAtDesc();
}

