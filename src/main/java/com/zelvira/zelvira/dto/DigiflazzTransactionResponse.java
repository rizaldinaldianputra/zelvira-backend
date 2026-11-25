package com.zelvira.zelvira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DigiflazzTransactionResponse {

    @JsonProperty("ref_id")
    private String refId;

    @JsonProperty("customer_no")
    private String customerNo;

    @JsonProperty("buyer_sku_code")
    private String buyerSkuCode;

    @JsonProperty("customer_name")
    private String customerName;

    private String message;
    private String status;
    private String rc;
    private String sn;

    @JsonProperty("buyer_last_saldo")
    private Double buyerLastSaldo;

    private Double price;

    @JsonProperty("selling_price")
    private Double sellingPrice;

    private Double admin;
    private String bank;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("account_no")
    private String accountNo;

    private String notes;
    private Double amount;
    private Object desc;
}
