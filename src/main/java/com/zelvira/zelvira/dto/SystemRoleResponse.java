package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemRoleResponse {
    private Long id;
    private String name;
    private String description;
    private Boolean isSystem;
    private Integer userCount;
    private List<RoleMenuPermissionDto> permissions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
