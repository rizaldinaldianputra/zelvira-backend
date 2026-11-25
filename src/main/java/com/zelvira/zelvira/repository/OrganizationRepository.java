package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findFirstByOrderByIdAsc();
}
