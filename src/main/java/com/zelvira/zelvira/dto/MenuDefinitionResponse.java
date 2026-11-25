package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuDefinitionResponse {
    private String group;
    private String menuKey;
    private String menuTitle;
    private String defaultPath;
}
