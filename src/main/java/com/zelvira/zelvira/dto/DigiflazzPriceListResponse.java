package com.zelvira.zelvira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DigiflazzPriceListResponse {

    @JsonProperty("product_name")
    private String productName;

    private String category;
    private String brand;
    private String type;

    @JsonProperty("seller_name")
    private String sellerName;

    private Double price;

    @JsonProperty("buyer_sku_code")
    private String buyerSkuCode;

    @JsonProperty("buyer_product_status")
    private Boolean buyerProductStatus;

    @JsonProperty("seller_product_status")
    private Boolean sellerProductStatus;

    @JsonProperty("unlimited_stock")
    private Boolean unlimitedStock;

    private Integer stock;
    private Boolean multi;

    @JsonProperty("start_cut_off")
    private String startCutOff;

    @JsonProperty("end_cut_off")
    private String endCutOff;

    private String desc;
    private Double admin;
    private Double commission;
}
