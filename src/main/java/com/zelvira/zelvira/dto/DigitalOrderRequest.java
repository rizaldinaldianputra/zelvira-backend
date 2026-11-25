package com.zelvira.zelvira.dto;

import lombok.Data;

@Data
public class DigitalOrderRequest {
    private String skuCode;
    private String customerNo;
    private String email;
}
