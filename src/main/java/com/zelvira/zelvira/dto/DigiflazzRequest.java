package com.zelvira.zelvira.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DigiflazzRequest {

    private String cmd;
    private String commands;
    private String username;
    private String sign;

    @JsonProperty("buyer_sku_code")
    private String buyerSkuCode;

    @JsonProperty("customer_no")
    private String customerNo;

    @JsonProperty("ref_id")
    private String refId;

    private Boolean testing;
    private String code;
    private String category;
    private String brand;
    private String type;

    private Long amount;
    private String bank;

    @JsonProperty("owner_name")
    private String ownerName;
}
