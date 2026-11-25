package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.*;

import java.util.List;

public interface PricingService {

    // Master Biaya
    List<PricingCostItemResponse> getAllCostItems();
    PricingCostItemResponse createCostItem(PricingCostItemRequest request);
    PricingCostItemResponse updateCostItem(Long id, PricingCostItemRequest request);
    void deleteCostItem(Long id);

    // Master Formula & Dynamic Expression
    List<PricingFormulaResponse> getAllFormulas();
    PricingFormulaResponse createFormula(PricingFormulaRequest request);
    PricingFormulaResponse updateFormula(Long id, PricingFormulaRequest request);
    void deleteFormula(Long id);

    // Price Calculation & Realtime Simulation
    PriceCalculationResponse calculatePrice(PriceCalculationRequest request);

    // Existing Master Product Pricing
    List<ProductPricingResponse> getProductPricingList(String search, Long categoryId);
    ProductPricingResponse applyPrice(ApplyPriceRequest request);
    int batchApplyPrices(List<ApplyPriceRequest> requests);

    // Profitability Analytics
    PricingAnalyticsResponse getPricingAnalytics();
}

