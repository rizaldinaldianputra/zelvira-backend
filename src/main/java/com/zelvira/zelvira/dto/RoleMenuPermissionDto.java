package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleMenuPermissionDto {
    private Long id;
    private String menuKey;
    private String menuTitle;
    private Boolean canView;
    private Boolean canRead;
    private Boolean canWrite;
}
