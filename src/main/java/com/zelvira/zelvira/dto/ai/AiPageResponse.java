package com.zelvira.zelvira.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPageResponse<T> {
    private List<T> items;
    private int page;
    private int limit;
    private long totalItems;
    private int totalPages;
}
