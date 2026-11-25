package com.zelvira.zelvira.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zelvira.zelvira.dto.*;
import com.zelvira.zelvira.entity.*;
import com.zelvira.zelvira.repository.*;
import com.zelvira.zelvira.service.PricingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingServiceImpl implements PricingService {

    private final PricingCostItemRepository costItemRepository;
    private final PricingFormulaRepository formulaRepository;
    private final ProductPricingProfileRepository profileRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final OrderItemRepository orderItemRepository;
    private final PricingFormulaEngine formulaEngine;
    private final ObjectMapper objectMapper;

    @PostConstruct
    @Transactional
    public void initDefaults() {
        try {
            initDefaultCostItems();
            initDefaultFormulas();
        } catch (Exception e) {
            log.warn("Could not initialize pricing defaults: {}", e.getMessage());
        }
    }

    private void initDefaultCostItems() {
        if (costItemRepository.count() == 0) {
            log.info("Seeding default pricing cost items...");
            List<PricingCostItem> items = List.of(
                    PricingCostItem.builder().code("PLASTIK").name("Plastik Polymailer (20x30)").category("PACKAGING").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("800.00")).unit("pcs").description("Kantong plastik luar pengiriman paket").build(),
                    PricingCostItem.builder().code("PLASTIK_ZIPPER").name("Plastik Zipper Pouch").category("PACKAGING").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("1500.00")).unit("pcs").description("Plastik klip kemasan pakaian premium").build(),
                    PricingCostItem.builder().code("HANGTAG").name("Hangtag & Tali Pengikat").category("PACKAGING").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("400.00")).unit("pcs").description("Label merk gantung pada pakaian").build(),
                    PricingCostItem.builder().code("LABEL_BARCODE").name("Stiker Barcode & Size").category("PACKAGING").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("250.00")).unit("pcs").description("Label thermal barcode SKU").build(),
                    PricingCostItem.builder().code("KOTAK_BOX").name("Karton Box Corrugated").category("PACKAGING").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("3500.00")).unit("pcs").description("Kardus kemasan produk tebal/sepatu").build(),
                    PricingCostItem.builder().code("BUBBLE_WRAP").name("Bubble Wrap Pengaman").category("PACKAGING").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("500.00")).unit("lembar").description("Pelindung benturan").build(),
                    PricingCostItem.builder().code("THANKYOU_CARD").name("Thank You Card & Voucher Promo").category("PACKAGING").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("300.00")).unit("lembar").description("Kartu ucapan terima kasih dalam paket").build(),
                    PricingCostItem.builder().code("QC_STEAM").name("QC, Buang Benang & Setrika Uap").category("OPERATIONAL").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("1000.00")).unit("pcs").description("Pembersihan dan steam produk").build(),
                    PricingCostItem.builder().code("PACKING_FEE").name("Ongkos Packing & Lakban").category("OPERATIONAL").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("1500.00")).unit("pcs").description("Upah tenaga kemas per paket").build(),
                    PricingCostItem.builder().code("IKLAN_ADS").name("Alokasi Biaya Iklan (Ads Cost per Pcs)").category("MARKETING").costType("FIXED_AMOUNT").defaultAmount(new BigDecimal("5000.00")).unit("pcs").description("Budget iklan per unit terjual").build(),
                    PricingCostItem.builder().code("ADMIN_FEE").name("Biaya Admin Marketplace / Payment").category("OPERATIONAL").costType("PERCENTAGE").defaultAmount(new BigDecimal("7.50")).unit("%").description("Estimasi potongan komisi admin").build()
            );
            costItemRepository.saveAll(items);
            log.info("Default pricing cost items seeded successfully.");
        }
    }

    private void initDefaultFormulas() {
        if (formulaRepository.count() == 0) {
            log.info("Seeding default pricing formulas...");
            PricingFormula standard = PricingFormula.builder()
                    .name("Formula Standar Retail & Web")
                    .code("STANDARD_RETAIL")
                    .description("Formula cerdas untuk menghitung HPP, batas bawah harga minimum, dan harga jual rekomendasi dengan margin aman.")
                    .hppExpression("MODAL + BIAYA_PACKING + BIAYA_OPERASIONAL + BIAYA_IKLAN + BIAYA_LAIN")
                    .minPriceExpression("HPP / (1.0 - (ADMIN_FEE_PERCENT / 100.0))")
                    .sellingPriceExpression("(HPP + PROFIT_NOMINAL) / (1.0 - (ADMIN_FEE_PERCENT / 100.0) - (PROFIT_PERCENT / 100.0))")
                    .strikePriceExpression("HARGA_JUAL / (1.0 - (BUFFER_DISKON_PERCENT / 100.0))")
                    .roundingRule("ROUND_UP_1000")
                    .defaultProfitPercent(new BigDecimal("30.00"))
                    .defaultProfitNominal(BigDecimal.ZERO)
                    .defaultAdminFeePercent(new BigDecimal("7.50"))
                    .defaultBufferDiskonPercent(new BigDecimal("20.00"))
                    .isDefault(true)
                    .build();

            PricingFormula marketplace = PricingFormula.builder()
                    .name("Formula Khusus Marketplace (TikTok / Shopee)")
                    .code("MARKETPLACE_HIGH_FEE")
                    .description("Formula dengan kompensasi potongan admin marketplace tinggi (9.5%) dan buffer promo flash sale.")
                    .hppExpression("MODAL + BIAYA_PACKING + BIAYA_OPERASIONAL + BIAYA_IKLAN + BIAYA_LAIN")
                    .minPriceExpression("HPP / (1.0 - (ADMIN_FEE_PERCENT / 100.0))")
                    .sellingPriceExpression("(HPP + PROFIT_NOMINAL) / (1.0 - (ADMIN_FEE_PERCENT / 100.0) - (PROFIT_PERCENT / 100.0))")
                    .strikePriceExpression("HARGA_JUAL / (1.0 - (BUFFER_DISKON_PERCENT / 100.0))")
                    .roundingRule("CHARM_900")
                    .defaultProfitPercent(new BigDecimal("35.00"))
                    .defaultProfitNominal(BigDecimal.ZERO)
                    .defaultAdminFeePercent(new BigDecimal("9.50"))
                    .defaultBufferDiskonPercent(new BigDecimal("25.00"))
                    .isDefault(false)
                    .build();

            formulaRepository.saveAll(List.of(standard, marketplace));
            log.info("Default pricing formulas seeded successfully.");
        }
    }

    // ==========================================
    // 1. MASTER BIAYA (COST ITEMS)
    // ==========================================

    @Override
    public List<PricingCostItemResponse> getAllCostItems() {
        return costItemRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .map(this::toCostItemResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PricingCostItemResponse createCostItem(PricingCostItemRequest req) {
        String code = req.getCode().trim().toUpperCase().replaceAll("\\s+", "_");
        if (costItemRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Kode biaya '" + code + "' sudah digunakan");
        }

        PricingCostItem item = PricingCostItem.builder()
                .code(code)
                .name(req.getName().trim())
                .category(req.getCategory() != null ? req.getCategory().toUpperCase() : "PACKAGING")
                .costType(req.getCostType() != null ? req.getCostType().toUpperCase() : "FIXED_AMOUNT")
                .defaultAmount(req.getDefaultAmount() != null ? req.getDefaultAmount() : BigDecimal.ZERO)
                .unit(req.getUnit() != null ? req.getUnit().trim() : "pcs")
                .description(req.getDescription())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();

        return toCostItemResponse(costItemRepository.save(item));
    }

    @Override
    @Transactional
    public PricingCostItemResponse updateCostItem(Long id, PricingCostItemRequest req) {
        PricingCostItem item = costItemRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Komponen biaya tidak ditemukan"));

        if (req.getName() != null) item.setName(req.getName().trim());
        if (req.getCategory() != null) item.setCategory(req.getCategory().toUpperCase());
        if (req.getCostType() != null) item.setCostType(req.getCostType().toUpperCase());
        if (req.getDefaultAmount() != null) item.setDefaultAmount(req.getDefaultAmount());
        if (req.getUnit() != null) item.setUnit(req.getUnit().trim());
        if (req.getDescription() != null) item.setDescription(req.getDescription());
        if (req.getIsActive() != null) item.setIsActive(req.getIsActive());

        return toCostItemResponse(costItemRepository.save(item));
    }

    @Override
    @Transactional
    public void deleteCostItem(Long id) {
        costItemRepository.deleteById(id);
    }

    // ==========================================
    // 2. MASTER FORMULA & DYNAMIC EXPRESSION
    // ==========================================

    @Override
    public List<PricingFormulaResponse> getAllFormulas() {
        return formulaRepository.findAll().stream()
                .map(this::toFormulaResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PricingFormulaResponse createFormula(PricingFormulaRequest req) {
        String code = req.getCode().trim().toUpperCase().replaceAll("\\s+", "_");
        if (formulaRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Kode formula '" + code + "' sudah digunakan");
        }

        if (Boolean.TRUE.equals(req.getIsDefault())) {
            unsetDefaultFormulas();
        }

        PricingFormula f = PricingFormula.builder()
                .name(req.getName().trim())
                .code(code)
                .description(req.getDescription())
                .hppExpression(req.getHppExpression() != null ? req.getHppExpression() : "MODAL + BIAYA_PACKING + BIAYA_OPERASIONAL + BIAYA_IKLAN + BIAYA_LAIN")
                .minPriceExpression(req.getMinPriceExpression() != null ? req.getMinPriceExpression() : "HPP / (1.0 - (ADMIN_FEE_PERCENT / 100.0))")
                .sellingPriceExpression(req.getSellingPriceExpression() != null ? req.getSellingPriceExpression() : "(HPP + PROFIT_NOMINAL) / (1.0 - (ADMIN_FEE_PERCENT / 100.0) - (PROFIT_PERCENT / 100.0))")
                .strikePriceExpression(req.getStrikePriceExpression() != null ? req.getStrikePriceExpression() : "HARGA_JUAL / (1.0 - (BUFFER_DISKON_PERCENT / 100.0))")
                .roundingRule(req.getRoundingRule() != null ? req.getRoundingRule() : "ROUND_UP_1000")
                .defaultProfitPercent(req.getDefaultProfitPercent() != null ? req.getDefaultProfitPercent() : new BigDecimal("30.00"))
                .defaultProfitNominal(req.getDefaultProfitNominal() != null ? req.getDefaultProfitNominal() : BigDecimal.ZERO)
                .defaultAdminFeePercent(req.getDefaultAdminFeePercent() != null ? req.getDefaultAdminFeePercent() : new BigDecimal("7.50"))
                .defaultBufferDiskonPercent(req.getDefaultBufferDiskonPercent() != null ? req.getDefaultBufferDiskonPercent() : new BigDecimal("20.00"))
                .isDefault(Boolean.TRUE.equals(req.getIsDefault()))
                .build();

        return toFormulaResponse(formulaRepository.save(f));
    }

    @Override
    @Transactional
    public PricingFormulaResponse updateFormula(Long id, PricingFormulaRequest req) {
        PricingFormula f = formulaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Formula harga tidak ditemukan"));

        if (Boolean.TRUE.equals(req.getIsDefault())) {
            unsetDefaultFormulas();
            f.setIsDefault(true);
        } else if (req.getIsDefault() != null) {
            f.setIsDefault(req.getIsDefault());
        }

        if (req.getName() != null) f.setName(req.getName().trim());
        if (req.getDescription() != null) f.setDescription(req.getDescription());
        if (req.getHppExpression() != null) f.setHppExpression(req.getHppExpression());
        if (req.getMinPriceExpression() != null) f.setMinPriceExpression(req.getMinPriceExpression());
        if (req.getSellingPriceExpression() != null) f.setSellingPriceExpression(req.getSellingPriceExpression());
        if (req.getStrikePriceExpression() != null) f.setStrikePriceExpression(req.getStrikePriceExpression());
        if (req.getRoundingRule() != null) f.setRoundingRule(req.getRoundingRule());
        if (req.getDefaultProfitPercent() != null) f.setDefaultProfitPercent(req.getDefaultProfitPercent());
        if (req.getDefaultProfitNominal() != null) f.setDefaultProfitNominal(req.getDefaultProfitNominal());
        if (req.getDefaultAdminFeePercent() != null) f.setDefaultAdminFeePercent(req.getDefaultAdminFeePercent());
        if (req.getDefaultBufferDiskonPercent() != null) f.setDefaultBufferDiskonPercent(req.getDefaultBufferDiskonPercent());

        return toFormulaResponse(formulaRepository.save(f));
    }

    private void unsetDefaultFormulas() {
        formulaRepository.findAll().forEach(item -> {
            if (Boolean.TRUE.equals(item.getIsDefault())) {
                item.setIsDefault(false);
                formulaRepository.save(item);
            }
        });
    }

    @Override
    @Transactional
    public void deleteFormula(Long id) {
        formulaRepository.deleteById(id);
    }

    // ==========================================
    // 3. KALKULASI HARGA & REALTIME SIMULATION
    // ==========================================

    @Override
    public PriceCalculationResponse calculatePrice(PriceCalculationRequest req) {
        // 1. Resolve Formula
        PricingFormula formula = null;
        if (req.getFormulaId() != null) {
            formula = formulaRepository.findById(req.getFormulaId()).orElse(null);
        }
        if (formula == null) {
            formula = formulaRepository.findFirstByIsDefaultTrue()
                    .orElseGet(() -> formulaRepository.findAll().stream().findFirst()
                            .orElse(PricingFormula.builder().build()));
        }

        // 2. Resolve Parameters
        BigDecimal modal = req.getBaseModal() != null ? req.getBaseModal() : BigDecimal.ZERO;
        if (modal.compareTo(BigDecimal.ZERO) == 0 && req.getVariantId() != null) {
            ProductVariant v = variantRepository.findById(req.getVariantId()).orElse(null);
            if (v != null && v.getCostPrice() != null) {
                modal = v.getCostPrice();
            }
        }

        BigDecimal profitPercent = req.getProfitPercent() != null ? req.getProfitPercent() : formula.getDefaultProfitPercent();
        BigDecimal profitNominal = req.getProfitNominal() != null ? req.getProfitNominal() : formula.getDefaultProfitNominal();
        BigDecimal adminFeePercent = req.getAdminFeePercent() != null ? req.getAdminFeePercent() : formula.getDefaultAdminFeePercent();
        BigDecimal bufferDiskonPercent = req.getBufferDiskonPercent() != null ? req.getBufferDiskonPercent() : formula.getDefaultBufferDiskonPercent();
        String roundingRule = req.getRoundingRule() != null ? req.getRoundingRule() : formula.getRoundingRule();

        // 3. Resolve Cost Items & Itemized Costs
        List<PricingCostItem> allItems = costItemRepository.findByIsActiveTrueOrderByCategoryAscNameAsc();
        Map<String, BigDecimal> itemized = new LinkedHashMap<>();
        BigDecimal packagingCost = BigDecimal.ZERO;
        BigDecimal operationalCost = BigDecimal.ZERO;
        BigDecimal marketingCost = BigDecimal.ZERO;
        BigDecimal otherCost = BigDecimal.ZERO;

        Map<String, Object> varMap = new HashMap<>();

        for (PricingCostItem item : allItems) {
            BigDecimal amount = item.getDefaultAmount();
            if (req.getCostOverrides() != null && req.getCostOverrides().containsKey(item.getCode())) {
                amount = req.getCostOverrides().get(item.getCode());
            }

            if ("PERCENTAGE".equalsIgnoreCase(item.getCostType())) {
                // If percentage, calculate based on modal
                amount = modal.multiply(amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            itemized.put(item.getCode(), amount);
            varMap.put(item.getCode(), amount);

            switch (item.getCategory().toUpperCase()) {
                case "PACKAGING":
                    packagingCost = packagingCost.add(amount);
                    break;
                case "OPERATIONAL":
                    operationalCost = operationalCost.add(amount);
                    break;
                case "MARKETING":
                    marketingCost = marketingCost.add(amount);
                    break;
                default:
                    otherCost = otherCost.add(amount);
                    break;
            }
        }

        // Put aggregated and standard variables in evaluation context
        varMap.put("MODAL", modal);
        varMap.put("BIAYA_PACKING", packagingCost);
        varMap.put("BIAYA_OPERASIONAL", operationalCost);
        varMap.put("BIAYA_IKLAN", marketingCost);
        varMap.put("BIAYA_LAIN", otherCost);
        varMap.put("PROFIT_PERCENT", profitPercent);
        varMap.put("PROFIT_NOMINAL", profitNominal);
        varMap.put("ADMIN_FEE_PERCENT", adminFeePercent);
        varMap.put("BUFFER_DISKON_PERCENT", bufferDiskonPercent);

        // 4. Evaluate Dynamic Expressions
        BigDecimal calculatedHpp = formulaEngine.evaluate(formula.getHppExpression(), varMap);
        if (calculatedHpp.compareTo(BigDecimal.ZERO) == 0) {
            calculatedHpp = modal.add(packagingCost).add(operationalCost).add(marketingCost).add(otherCost);
        }
        varMap.put("HPP", calculatedHpp);

        BigDecimal minPrice = formulaEngine.evaluate(formula.getMinPriceExpression(), varMap);
        if (minPrice.compareTo(BigDecimal.ZERO) <= 0) {
            double rate = 1.0 - (adminFeePercent.doubleValue() / 100.0);
            minPrice = rate > 0 ? calculatedHpp.divide(BigDecimal.valueOf(rate), 2, RoundingMode.HALF_UP) : calculatedHpp;
        }

        BigDecimal rawSellingPrice = formulaEngine.evaluate(formula.getSellingPriceExpression(), varMap);
        if (rawSellingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            double rate = 1.0 - (adminFeePercent.doubleValue() / 100.0) - (profitPercent.doubleValue() / 100.0);
            BigDecimal targetNumerator = calculatedHpp.add(profitNominal);
            rawSellingPrice = rate > 0 ? targetNumerator.divide(BigDecimal.valueOf(rate), 2, RoundingMode.HALF_UP) : targetNumerator;
        }

        BigDecimal roundedPrice = formulaEngine.applyRounding(rawSellingPrice, roundingRule);
        varMap.put("HARGA_JUAL", roundedPrice);

        BigDecimal rawStrikePrice = formulaEngine.evaluate(formula.getStrikePriceExpression(), varMap);
        if (rawStrikePrice.compareTo(BigDecimal.ZERO) <= 0) {
            double rate = 1.0 - (bufferDiskonPercent.doubleValue() / 100.0);
            rawStrikePrice = rate > 0 ? roundedPrice.divide(BigDecimal.valueOf(rate), 2, RoundingMode.HALF_UP) : roundedPrice;
        }
        BigDecimal roundedStrikePrice = formulaEngine.applyRounding(rawStrikePrice, roundingRule);

        // Calculate actual realized profit nominal and margin percent with final rounded price
        BigDecimal estimatedAdminFee = roundedPrice.multiply(adminFeePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal netReceived = roundedPrice.subtract(estimatedAdminFee);
        BigDecimal finalProfit = netReceived.subtract(calculatedHpp);
        BigDecimal finalMarginPercent = roundedPrice.compareTo(BigDecimal.ZERO) > 0
                ? finalProfit.divide(roundedPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, BigDecimal> variablesUsed = new LinkedHashMap<>();
        varMap.forEach((k, v) -> {
            if (v instanceof BigDecimal) variablesUsed.put(k, (BigDecimal) v);
            else if (v instanceof Number) variablesUsed.put(k, BigDecimal.valueOf(((Number) v).doubleValue()));
        });

        return PriceCalculationResponse.builder()
                .baseModal(modal)
                .packagingCost(packagingCost)
                .operationalCost(operationalCost)
                .marketingCost(marketingCost)
                .otherCost(otherCost)
                .totalHpp(calculatedHpp)
                .minSellingPrice(minPrice)
                .recommendedPrice(rawSellingPrice)
                .roundedPrice(roundedPrice)
                .strikePrice(roundedStrikePrice)
                .profitNominal(finalProfit)
                .marginPercent(finalMarginPercent)
                .estimatedAdminFeeNominal(estimatedAdminFee)
                .roundingRule(roundingRule)
                .variablesUsed(variablesUsed)
                .itemizedCosts(itemized)
                .build();
    }

    // ==========================================
    // 4. EXISTING MASTER PRODUCT PRICING INTEGRATION
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public List<ProductPricingResponse> getProductPricingList(String search, Long categoryId) {
        List<Product> products = productRepository.findAll();

        if (categoryId != null) {
            products = products.stream()
                    .filter(p -> categoryId.equals(p.getCategoryId()))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase().trim();
            products = products.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(q)) ||
                                 (p.getSku() != null && p.getSku().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        PricingFormula defaultFormula = formulaRepository.findFirstByIsDefaultTrue()
                .orElseGet(() -> formulaRepository.findAll().stream().findFirst().orElse(null));

        List<ProductPricingResponse> result = new ArrayList<>();

        for (Product product : products) {
            List<ProductVariant> variants = product.getVariants() != null ? product.getVariants() : variantRepository.findByProductId(product.getId());

            BigDecimal minPrice = null;
            BigDecimal maxPrice = null;
            BigDecimal avgCost = BigDecimal.ZERO;
            int totalStock = 0;
            int costCount = 0;

            List<ProductPricingResponse.VariantPricingDto> varDtos = new ArrayList<>();

            for (ProductVariant v : variants) {
                BigDecimal p = v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO;
                BigDecimal c = v.getCostPrice() != null ? v.getCostPrice() : BigDecimal.ZERO;
                int stk = v.getStock() != null ? v.getStock() : 0;
                totalStock += stk;

                if (minPrice == null || p.compareTo(minPrice) < 0) minPrice = p;
                if (maxPrice == null || p.compareTo(maxPrice) > 0) maxPrice = p;

                if (c.compareTo(BigDecimal.ZERO) > 0) {
                    avgCost = avgCost.add(c);
                    costCount++;
                }

                // Check profile if exists
                ProductPricingProfile prof = profileRepository.findByProductIdAndVariantId(product.getId(), v.getId()).orElse(null);
                BigDecimal vHpp = prof != null ? prof.getCalculatedHpp() : c;
                BigDecimal vSell = prof != null ? prof.getCalculatedSellingPrice() : p;
                BigDecimal vStrike = prof != null ? prof.getCalculatedStrikePrice() : v.getDiscountPrice();
                BigDecimal vMargin = prof != null ? prof.getTargetProfitPercent() : BigDecimal.ZERO;

                if ((vMargin == null || vMargin.compareTo(BigDecimal.ZERO) == 0) && vSell != null && vSell.compareTo(BigDecimal.ZERO) > 0 && vHpp != null) {
                    BigDecimal profit = vSell.subtract(vHpp);
                    vMargin = profit.divide(vSell, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                }

                varDtos.add(ProductPricingResponse.VariantPricingDto.builder()
                        .id(v.getId())
                        .sku(v.getSku())
                        .size(v.getSize())
                        .color(v.getColor())
                        .stock(stk)
                        .currentPrice(p)
                        .currentDiscountPrice(v.getDiscountPrice())
                        .currentCostPrice(c)
                        .calculatedHpp(vHpp)
                        .calculatedSellingPrice(vSell)
                        .calculatedStrikePrice(vStrike)
                        .profitMarginPercent(vMargin)
                        .isApplied(prof != null && Boolean.TRUE.equals(prof.getIsApplied()))
                        .build());
            }

            BigDecimal costPriceAvg = costCount > 0 ? avgCost.divide(BigDecimal.valueOf(costCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            ProductPricingProfile parentProfile = profileRepository.findFirstByProductIdAndVariantIdIsNull(product.getId()).orElse(null);

            String firstImg = null;
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                firstImg = product.getImages().get(0).getUrl();
            }

            result.add(ProductPricingResponse.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .sku(product.getSku())
                    .categoryName(product.getCategory() != null ? product.getCategory().getName() : "-")
                    .imageUrl(firstImg)
                    .totalStock(totalStock)
                    .currentMinPrice(minPrice != null ? minPrice : BigDecimal.ZERO)
                    .currentMaxPrice(maxPrice != null ? maxPrice : BigDecimal.ZERO)
                    .currentCostPrice(costPriceAvg)
                    .calculatedHpp(parentProfile != null ? parentProfile.getCalculatedHpp() : costPriceAvg)
                    .calculatedSellingPrice(parentProfile != null ? parentProfile.getCalculatedSellingPrice() : (minPrice != null ? minPrice : BigDecimal.ZERO))
                    .calculatedStrikePrice(parentProfile != null ? parentProfile.getCalculatedStrikePrice() : null)
                    .profitMarginPercent(parentProfile != null ? parentProfile.getTargetProfitPercent() : BigDecimal.ZERO)
                    .isApplied(parentProfile != null && Boolean.TRUE.equals(parentProfile.getIsApplied()))
                    .variants(varDtos)
                    .build());
        }

        return result;
    }

    @Override
    @Transactional
    public ProductPricingResponse applyPrice(ApplyPriceRequest req) {
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new NoSuchElementException("Produk tidak ditemukan"));

        List<ProductVariant> variants = variantRepository.findByProductId(product.getId());
        List<ProductVariant> targetVariants = new ArrayList<>();

        if (req.getVariantId() != null) {
            targetVariants = variants.stream().filter(v -> v.getId().equals(req.getVariantId())).collect(Collectors.toList());
        } else if (req.getVariantIds() != null && !req.getVariantIds().isEmpty()) {
            Set<Long> set = new HashSet<>(req.getVariantIds());
            targetVariants = variants.stream().filter(v -> set.contains(v.getId())).collect(Collectors.toList());
        } else {
            // Apply to all variants
            targetVariants = variants;
        }

        for (ProductVariant v : targetVariants) {
            if (req.getSellingPrice() != null) {
                v.setPrice(req.getSellingPrice());
            }
            if (req.getDiscountPrice() != null) {
                v.setDiscountPrice(req.getDiscountPrice());
            }
            if (req.getCostPrice() != null) {
                v.setCostPrice(req.getCostPrice());
            }
            variantRepository.save(v);

            // Save or update profile for variant
            ProductPricingProfile profile = profileRepository.findByProductIdAndVariantId(product.getId(), v.getId())
                    .orElseGet(() -> ProductPricingProfile.builder().productId(product.getId()).variantId(v.getId()).build());

            profile.setCalculatedSellingPrice(req.getSellingPrice());
            profile.setCalculatedStrikePrice(req.getDiscountPrice());
            if (req.getCostPrice() != null) profile.setCalculatedHpp(req.getCostPrice());
            profile.setIsApplied(true);
            profile.setLastAppliedAt(LocalDateTime.now());
            profileRepository.save(profile);
        }

        // Save or update parent profile
        ProductPricingProfile parentProf = profileRepository.findFirstByProductIdAndVariantIdIsNull(product.getId())
                .orElseGet(() -> ProductPricingProfile.builder().productId(product.getId()).build());
        parentProf.setCalculatedSellingPrice(req.getSellingPrice());
        parentProf.setCalculatedStrikePrice(req.getDiscountPrice());
        if (req.getCostPrice() != null) parentProf.setCalculatedHpp(req.getCostPrice());
        parentProf.setIsApplied(true);
        parentProf.setLastAppliedAt(LocalDateTime.now());
        profileRepository.save(parentProf);

        List<ProductPricingResponse> updated = getProductPricingList(product.getName(), null);
        return updated.isEmpty() ? null : updated.get(0);
    }

    @Override
    @Transactional
    public int batchApplyPrices(List<ApplyPriceRequest> requests) {
        int count = 0;
        for (ApplyPriceRequest r : requests) {
            try {
                applyPrice(r);
                count++;
            } catch (Exception e) {
                log.warn("Failed to apply price for productId {}: {}", r.getProductId(), e.getMessage());
            }
        }
        return count;
    }

    // ==========================================
    // 5. ANALISIS KEUNTUNGAN (PROFITABILITY ANALYTICS)
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public PricingAnalyticsResponse getPricingAnalytics() {
        List<Product> products = productRepository.findAll();
        List<OrderItem> orderItems = orderItemRepository.findAll();

        // Map sales by productId
        Map<Long, Integer> qtySoldByProduct = new HashMap<>();
        Map<Long, BigDecimal> revenueByProduct = new HashMap<>();

        for (OrderItem oi : orderItems) {
            Long pid = oi.getProductId();
            int qty = oi.getQuantity() != null ? oi.getQuantity() : 0;
            double p = oi.getPrice() != null ? oi.getPrice() : 0.0;
            BigDecimal lineRev = BigDecimal.valueOf(qty * p);

            qtySoldByProduct.put(pid, qtySoldByProduct.getOrDefault(pid, 0) + qty);
            revenueByProduct.put(pid, revenueByProduct.getOrDefault(pid, BigDecimal.ZERO).add(lineRev));
        }

        BigDecimal totalInventoryHpp = BigDecimal.ZERO;
        BigDecimal totalAllRevenue = BigDecimal.ZERO;
        BigDecimal totalAllCost = BigDecimal.ZERO;
        BigDecimal totalAllProfit = BigDecimal.ZERO;

        List<PricingAnalyticsResponse.ProductProfitabilityDto> dtoList = new ArrayList<>();
        long profitableCount = 0;
        long lowMarginCount = 0;

        for (Product product : products) {
            List<ProductVariant> variants = product.getVariants() != null ? product.getVariants() : variantRepository.findByProductId(product.getId());

            int totalStock = 0;
            BigDecimal avgPrice = BigDecimal.ZERO;
            BigDecimal avgCost = BigDecimal.ZERO;
            int vCount = 0;

            for (ProductVariant v : variants) {
                int stk = v.getStock() != null ? v.getStock() : 0;
                totalStock += stk;
                BigDecimal cost = v.getCostPrice() != null ? v.getCostPrice() : BigDecimal.ZERO;
                BigDecimal price = v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO;

                totalInventoryHpp = totalInventoryHpp.add(cost.multiply(BigDecimal.valueOf(stk)));

                avgPrice = avgPrice.add(price);
                avgCost = avgCost.add(cost);
                vCount++;
            }

            BigDecimal currentPrice = vCount > 0 ? avgPrice.divide(BigDecimal.valueOf(vCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal hpp = vCount > 0 ? avgCost.divide(BigDecimal.valueOf(vCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            BigDecimal unitProfit = currentPrice.subtract(hpp);
            BigDecimal marginPercent = currentPrice.compareTo(BigDecimal.ZERO) > 0
                    ? unitProfit.divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            int unitsSold = qtySoldByProduct.getOrDefault(product.getId(), 0);
            BigDecimal revenue = revenueByProduct.getOrDefault(product.getId(), BigDecimal.ZERO);
            BigDecimal totalCost = hpp.multiply(BigDecimal.valueOf(unitsSold));
            BigDecimal totalProfit = revenue.subtract(totalCost);

            totalAllRevenue = totalAllRevenue.add(revenue);
            totalAllCost = totalAllCost.add(totalCost);
            totalAllProfit = totalAllProfit.add(totalProfit);

            if (marginPercent.compareTo(BigDecimal.valueOf(25)) >= 0) {
                profitableCount++;
            } else if (marginPercent.compareTo(BigDecimal.valueOf(15)) < 0) {
                lowMarginCount++;
            }

            String performance;
            if (unitsSold >= 5 && marginPercent.compareTo(BigDecimal.valueOf(25)) >= 0) {
                performance = "STAR"; // High sales, high margin
            } else if (unitsSold >= 5) {
                performance = "VOLUME_DRIVER"; // High sales, low/moderate margin
            } else if (marginPercent.compareTo(BigDecimal.valueOf(15)) < 0) {
                performance = "LOW_PERFORMER"; // Low sales, low margin
            } else {
                performance = "MODERATE";
            }

            String img = (product.getImages() != null && !product.getImages().isEmpty())
                    ? product.getImages().get(0).getUrl() : null;

            dtoList.add(PricingAnalyticsResponse.ProductProfitabilityDto.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .sku(product.getSku())
                    .category(product.getCategory() != null ? product.getCategory().getName() : "-")
                    .imageUrl(img)
                    .totalStock(totalStock)
                    .currentSellingPrice(currentPrice)
                    .hppPerUnit(hpp)
                    .unitProfit(unitProfit)
                    .marginPercent(marginPercent)
                    .unitsSold(unitsSold)
                    .revenue(revenue)
                    .totalCost(totalCost)
                    .totalProfit(totalProfit)
                    .performanceLabel(performance)
                    .build());
        }

        // Sort products by profit or revenue desc
        dtoList.sort((a, b) -> b.getTotalProfit().compareTo(a.getTotalProfit()));

        BigDecimal avgStoreMargin = totalAllRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalAllProfit.divide(totalAllRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return PricingAnalyticsResponse.builder()
                .totalInventoryHppValue(totalInventoryHpp)
                .totalRevenue(totalAllRevenue)
                .totalEstimatedHppCost(totalAllCost)
                .totalGrossProfit(totalAllProfit)
                .averageMarginPercent(avgStoreMargin)
                .totalProductsCount((long) products.size())
                .profitableProductsCount(profitableCount)
                .lowMarginProductsCount(lowMarginCount)
                .products(dtoList)
                .build();
    }

    // ==========================================
    // MAPPERS
    // ==========================================

    private PricingCostItemResponse toCostItemResponse(PricingCostItem i) {
        return PricingCostItemResponse.builder()
                .id(i.getId())
                .code(i.getCode())
                .name(i.getName())
                .category(i.getCategory())
                .costType(i.getCostType())
                .defaultAmount(i.getDefaultAmount())
                .unit(i.getUnit())
                .description(i.getDescription())
                .isActive(i.getIsActive())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }

    private PricingFormulaResponse toFormulaResponse(PricingFormula f) {
        return PricingFormulaResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .code(f.getCode())
                .description(f.getDescription())
                .hppExpression(f.getHppExpression())
                .minPriceExpression(f.getMinPriceExpression())
                .sellingPriceExpression(f.getSellingPriceExpression())
                .strikePriceExpression(f.getStrikePriceExpression())
                .roundingRule(f.getRoundingRule())
                .defaultProfitPercent(f.getDefaultProfitPercent())
                .defaultProfitNominal(f.getDefaultProfitNominal())
                .defaultAdminFeePercent(f.getDefaultAdminFeePercent())
                .defaultBufferDiskonPercent(f.getDefaultBufferDiskonPercent())
                .isDefault(f.getIsDefault())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}

