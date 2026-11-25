package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.SystemRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemRoleRepository extends JpaRepository<SystemRole, Long> {
    Optional<SystemRole> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
