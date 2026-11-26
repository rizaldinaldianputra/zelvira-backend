package com.zelvira.zelvira.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String profile;
    private String fetchedAt;

    public static <T> AiApiResponse<T> ok(T data, String profile) {
        return AiApiResponse.<T>builder()
                .success(true)
                .data(data)
                .profile(profile)
                .fetchedAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .build();
    }

    public static <T> AiApiResponse<T> error(String message, String profile) {
        return AiApiResponse.<T>builder()
                .success(false)
                .message(message)
                .profile(profile)
                .fetchedAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .build();
    }
}
