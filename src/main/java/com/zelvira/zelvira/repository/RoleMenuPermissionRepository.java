package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.RoleMenuPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleMenuPermissionRepository extends JpaRepository<RoleMenuPermission, Long> {
    List<RoleMenuPermission> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);
}
