package com.zelvira.zelvira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DigiflazzResponse<T> {

    private T data;
}
