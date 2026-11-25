package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.DigiflazzPriceListResponse;
import com.zelvira.zelvira.dto.DigiflazzResponse;
import com.zelvira.zelvira.entity.DigitalCategory;
import com.zelvira.zelvira.entity.DigitalProduct;
import com.zelvira.zelvira.repository.DigitalCategoryRepository;
import com.zelvira.zelvira.repository.DigitalProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DigitalProductService {

    private final DigitalProductRepository digitalProductRepository;
    private final DigitalCategoryRepository digitalCategoryRepository;
    private final DigiflazzService digiflazzService;
    private final com.zelvira.zelvira.repository.MobileAppSettingRepository mobileAppSettingRepository;

    public DigitalProductService(
            DigitalProductRepository digitalProductRepository,
            DigitalCategoryRepository digitalCategoryRepository,
            DigiflazzService digiflazzService,
            com.zelvira.zelvira.repository.MobileAppSettingRepository mobileAppSettingRepository) {
        this.digitalProductRepository = digitalProductRepository;
        this.digitalCategoryRepository = digitalCategoryRepository;
        this.digiflazzService = digiflazzService;
        this.mobileAppSettingRepository = mobileAppSettingRepository;
    }

    @jakarta.annotation.PostConstruct
    public void initCategoriesIfEmpty() {
        try {
            if (digitalCategoryRepository.count() == 0) {
                List<DigitalCategory> defaults = List.of(
                        // Prabayar
                        DigitalCategory.builder().code("pulsa").name("Pulsa").isPostpaid(false).isActive(true)
                                .iconName("phone_android").colorHex("#2196F3").sortOrder(1).build(),
                        DigitalCategory.builder().code("data").name("Paket Data").isPostpaid(false).isActive(true)
                                .iconName("wifi").colorHex("#4CAF50").sortOrder(2).build(),
                        DigitalCategory.builder().code("pln").name("Token PLN").isPostpaid(false).isActive(true)
                                .iconName("electric_bolt").colorHex("#FFA000").sortOrder(3).build(),
                        DigitalCategory.builder().code("emoney").name("E-Money").isPostpaid(false).isActive(true)
                                .iconName("account_balance_wallet").colorHex("#9C27B0").sortOrder(4).build(),
                        DigitalCategory.builder().code("game").name("Voucher Game").isPostpaid(false).isActive(true)
                                .iconName("sports_esports").colorHex("#FF5252").sortOrder(5).build(),
                        DigitalCategory.builder().code("voucher").name("Voucher Digital").isPostpaid(false)
                                .isActive(true).iconName("confirmation_number").colorHex("#009688").sortOrder(6)
                                .build(),
                        DigitalCategory.builder().code("paket").name("Paket Telpon & SMS").isPostpaid(false)
                                .isActive(true).iconName("call").colorHex("#3F51B5").sortOrder(7).build(),
                        DigitalCategory.builder().code("masa aktif").name("Masa Aktif").isPostpaid(false).isActive(true)
                                .iconName("access_time").colorHex("#FF9800").sortOrder(8).build(),
                        // Pascabayar
                        DigitalCategory.builder().code("pln").name("Tagihan PLN").isPostpaid(true).isActive(true)
                                .iconName("power").colorHex("#F57C00").sortOrder(10).build(),
                        DigitalCategory.builder().code("bpjs").name("BPJS Kesehatan").isPostpaid(true).isActive(true)
                                .iconName("health_and_safety").colorHex("#009688").sortOrder(11).build(),
                        DigitalCategory.builder().code("pdam").name("PDAM Air").isPostpaid(true).isActive(true)
                                .iconName("water_drop").colorHex("#03A9F4").sortOrder(12).build(),
                        DigitalCategory.builder().code("internet").name("Internet & TV").isPostpaid(true).isActive(true)
                                .iconName("router").colorHex("#673AB7").sortOrder(13).build(),
                        DigitalCategory.builder().code("pasca").name("Pascabayar HP").isPostpaid(true).isActive(true)
                                .iconName("phone_iphone").colorHex("#607D8B").sortOrder(14).build(),
                        DigitalCategory.builder().code("gas").name("Gas PGN").isPostpaid(true).isActive(true)
                                .iconName("local_fire_department").colorHex("#FF5722").sortOrder(15).build(),
                        DigitalCategory.builder().code("finance").name("Multifinance").isPostpaid(true).isActive(true)
                                .iconName("credit_card").colorHex("#795548").sortOrder(16).build());
                digitalCategoryRepository.saveAll(defaults);
                log.info("Initialized {} default digital categories.", defaults.size());
            }
        } catch (Exception e) {
            log.warn("Could not auto-initialize digital categories: {}", e.getMessage());
        }
    }

    // Sync Prepaid Products
    public int syncPrepaidProducts() {
        DigiflazzResponse<List<DigiflazzPriceListResponse>> response = digiflazzService.getPriceList("prepaid");
        if (response != null && response.getData() != null && !response.getData().isEmpty()) {
            return clearAndSaveProducts(response.getData(), false);
        } else {
            log.warn("Digiflazz prepaid pricelist is empty or failed to respond.");
            return 0;
        }
    }

    // Sync Postpaid Products
    public int syncPostpaidProducts() {
        DigiflazzResponse<List<DigiflazzPriceListResponse>> response = digiflazzService.getPriceList("pasca");
        if (response != null && response.getData() != null && !response.getData().isEmpty()) {
            return clearAndSaveProducts(response.getData(), true);
        } else {
            log.warn("Digiflazz postpaid pricelist is empty or failed to respond.");
            return 0;
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public int clearAndSaveProducts(List<DigiflazzPriceListResponse> products, boolean isPostpaid) {
        // 1. Simpan harga jual kustom lama jika SKU masih ada (opsional)
        Map<String, Double> existingPrices = digitalProductRepository.findByIsPostpaid(isPostpaid).stream()
                .filter(p -> p.getBuyerSkuCode() != null && p.getSellingPrice() != null)
                .collect(Collectors.toMap(
                        DigitalProduct::getBuyerSkuCode,
                        DigitalProduct::getSellingPrice,
                        (a, b) -> a));

        // 2. Simpan status aktif/inaktif kustom lama
        Map<String, Boolean> existingActiveStatus = digitalProductRepository.findByIsPostpaid(isPostpaid).stream()
                .filter(p -> p.getBuyerSkuCode() != null && p.getIsActive() != null)
                .collect(Collectors.toMap(
                        DigitalProduct::getBuyerSkuCode,
                        DigitalProduct::getIsActive,
                        (a, b) -> a));

        // 3. HAPUS dan CLEAR semua data lama sesuai tipe (prabayar atau pascabayar)
        digitalProductRepository.deleteByIsPostpaid(isPostpaid);
        digitalProductRepository.flush();

        // 4. Masukkan data baru segar dari Digiflazz (dengan deduplikasi buyerSkuCode)
        Map<String, DigitalProduct> uniqueProducts = new java.util.LinkedHashMap<>();
        for (DigiflazzPriceListResponse p : products) {
            if (p.getBuyerSkuCode() == null || uniqueProducts.containsKey(p.getBuyerSkuCode())) {
                continue;
            }

            Double price = p.getPrice() != null ? p.getPrice() : 0.0;
            Double sellingPrice = existingPrices.get(p.getBuyerSkuCode());
            if (sellingPrice == null) {
                if (isPostpaid) {
                    sellingPrice = (p.getAdmin() != null && p.getAdmin() > 0) ? p.getAdmin() : 2500.0;
                } else {
                    sellingPrice = price + 2000.0;
                }
            }

            Boolean isActive = existingActiveStatus.getOrDefault(p.getBuyerSkuCode(), true);

            DigitalProduct product = DigitalProduct.builder()
                    .productName(p.getProductName())
                    .category(p.getCategory())
                    .brand(p.getBrand())
                    .type(p.getType())
                    .sellerName(p.getSellerName())
                    .buyerSkuCode(p.getBuyerSkuCode())
                    .price(price)
                    .sellingPrice(sellingPrice)
                    .buyerProductStatus(p.getBuyerProductStatus() != null ? p.getBuyerProductStatus() : true)
                    .sellerProductStatus(p.getSellerProductStatus() != null ? p.getSellerProductStatus() : true)
                    .isActive(isActive)
                    .unlimitedStock(p.getUnlimitedStock() != null ? p.getUnlimitedStock() : true)
                    .stock(p.getStock())
                    .multi(p.getMulti())
                    .startCutOff(p.getStartCutOff())
                    .endCutOff(p.getEndCutOff())
                    .desc(p.getDesc())
                    .admin(p.getAdmin())
                    .commission(p.getCommission())
                    .isPostpaid(isPostpaid)
                    .build();

            uniqueProducts.put(p.getBuyerSkuCode(), product);
        }

        digitalProductRepository.saveAll(uniqueProducts.values());
        log.info("Berhasil membersihkan data lama dan menyimpan {} produk baru {} dari Digiflazz.",
                uniqueProducts.size(), isPostpaid ? "Pascabayar" : "Prabayar");
        return uniqueProducts.size();
    }

    public List<DigitalProduct> getAllProducts() {
        return digitalProductRepository.findAll();
    }

    public List<DigitalProduct> getProducts(String category, Boolean isPostpaid, Boolean includeInactive) {
        boolean onlyActive = (includeInactive == null || !includeInactive);
        if (category != null && !category.trim().isEmpty()) {
            String cat = category.trim();
            if ("emoney".equalsIgnoreCase(cat) || "e-money".equalsIgnoreCase(cat)) {
                cat = "money";
            } else if ("game".equalsIgnoreCase(cat)) {
                cat = "game";
            }
            if (isPostpaid != null) {
                if (onlyActive) {
                    return digitalProductRepository.findByCategoryContainingIgnoreCaseAndIsPostpaidAndIsActive(cat,
                            isPostpaid, true);
                }
                return digitalProductRepository.findByCategoryContainingIgnoreCaseAndIsPostpaid(cat, isPostpaid);
            }
            if (onlyActive) {
                return digitalProductRepository.findByCategoryContainingIgnoreCaseAndIsActive(cat, true);
            }
            return digitalProductRepository.findByCategoryContainingIgnoreCase(cat);
        }
        if (isPostpaid != null) {
            if (onlyActive) {
                return digitalProductRepository.findByIsPostpaidAndIsActive(isPostpaid, true);
            }
            return digitalProductRepository.findByIsPostpaid(isPostpaid);
        }
        if (onlyActive) {
            return digitalProductRepository.findByIsActive(true);
        }
        return digitalProductRepository.findAll();
    }

    public List<DigitalProduct> getProducts(String category, Boolean isPostpaid) {
        return getProducts(category, isPostpaid, false);
    }

    public List<DigitalProduct> getProductsByCategory(String category) {
        return getProducts(category, null, false);
    }

    public List<String> getAllCategories(Boolean isPostpaid, Boolean onlyActive) {
        initCategoriesIfEmpty();
        if (onlyActive != null && onlyActive) {
            List<DigitalCategory> cats = (isPostpaid != null)
                    ? digitalCategoryRepository.findByIsPostpaidAndIsActiveOrderBySortOrderAsc(isPostpaid, true)
                    : digitalCategoryRepository.findByIsActiveOrderBySortOrderAsc(true);
            return cats.stream().map(DigitalCategory::getCode).distinct().toList();
        }
        if (isPostpaid != null) {
            return digitalProductRepository.findDistinctCategoriesByIsPostpaid(isPostpaid);
        }
        return digitalProductRepository.findDistinctCategories();
    }

    public List<String> getAllCategories(Boolean isPostpaid) {
        return getAllCategories(isPostpaid, true);
    }

    public List<DigitalCategory> getCategoryList(Boolean isPostpaid, Boolean onlyActive) {
        initCategoriesIfEmpty();
        if (onlyActive != null && onlyActive) {
            com.zelvira.zelvira.entity.MobileAppSetting setting = mobileAppSettingRepository.findFirstByOrderByIdAsc().orElse(null);
            boolean isEnabled = (setting != null && setting.getDigitalProductEnabled() != null)
                    ? setting.getDigitalProductEnabled()
                    : true;
            if (!isEnabled) {
                return java.util.Collections.emptyList();
            }
            long activeCount = digitalProductRepository.countByIsActiveTrue();
            if (activeCount == 0) {
                return java.util.Collections.emptyList();
            }

            List<DigitalCategory> rawCats = (isPostpaid != null)
                    ? digitalCategoryRepository.findByIsPostpaidAndIsActiveOrderBySortOrderAsc(isPostpaid, true)
                    : digitalCategoryRepository.findByIsActiveOrderBySortOrderAsc(true);

            return rawCats.stream().filter(cat -> {
                String search = cat.getCode();
                if ("emoney".equalsIgnoreCase(search) || "e-money".equalsIgnoreCase(search)) {
                    search = "money";
                }
                return digitalProductRepository.existsByCategoryContainingIgnoreCaseAndIsPostpaidAndIsActive(search, cat.getIsPostpaid(), true);
            }).collect(Collectors.toList());
        }
        if (isPostpaid != null) {
            return digitalCategoryRepository.findByIsPostpaidOrderBySortOrderAsc(isPostpaid);
        }
        return digitalCategoryRepository.findAllByOrderBySortOrderAsc();
    }

    public Map<String, Object> getDigitalProductConfig() {
        com.zelvira.zelvira.entity.MobileAppSetting setting = mobileAppSettingRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            com.zelvira.zelvira.entity.MobileAppSetting s = new com.zelvira.zelvira.entity.MobileAppSetting();
            return mobileAppSettingRepository.save(s);
        });

        long activeCount = digitalProductRepository.countByIsActiveTrue();
        boolean isEnabled = setting.getDigitalProductEnabled() != null ? setting.getDigitalProductEnabled() : true;
        String title = setting.getDigitalProductTitle() != null ? setting.getDigitalProductTitle() : "Digital Produk";
        String iconUrl = setting.getDigitalProductIconUrl() != null ? setting.getDigitalProductIconUrl() : "";

        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("enabled", isEnabled);
        config.put("title", title);
        config.put("iconUrl", iconUrl);
        config.put("activeCount", activeCount);
        config.put("hasActiveProducts", isEnabled && activeCount > 0);
        return config;
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> updateDigitalProductConfig(Map<String, Object> payload) {
        com.zelvira.zelvira.entity.MobileAppSetting setting = mobileAppSettingRepository.findFirstByOrderByIdAsc().orElseGet(com.zelvira.zelvira.entity.MobileAppSetting::new);

        if (payload.containsKey("enabled")) {
            setting.setDigitalProductEnabled(Boolean.parseBoolean(String.valueOf(payload.get("enabled"))));
        }
        if (payload.containsKey("title") && payload.get("title") != null) {
            setting.setDigitalProductTitle(String.valueOf(payload.get("title")).trim());
        }
        if (payload.containsKey("iconUrl")) {
            setting.setDigitalProductIconUrl(payload.get("iconUrl") != null ? String.valueOf(payload.get("iconUrl")).trim() : "");
        }

        mobileAppSettingRepository.save(setting);
        return getDigitalProductConfig();
    }

    @org.springframework.transaction.annotation.Transactional
    public DigitalProduct toggleProductStatus(Long id, Boolean active) {
        DigitalProduct product = digitalProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Digital product not found for ID: " + id));
        boolean newStatus = (active != null) ? active : !Boolean.TRUE.equals(product.getIsActive());
        product.setIsActive(newStatus);
        return digitalProductRepository.save(product);
    }

    @org.springframework.transaction.annotation.Transactional
    public DigitalCategory toggleCategoryStatus(Long id, Boolean active) {
        DigitalCategory cat = digitalCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Digital category not found for ID: " + id));
        boolean newStatus = (active != null) ? active : !Boolean.TRUE.equals(cat.getIsActive());
        cat.setIsActive(newStatus);
        digitalCategoryRepository.save(cat);

        // Also update products corresponding to this category
        String searchCategory = cat.getCode();
        if ("emoney".equalsIgnoreCase(searchCategory) || "e-money".equalsIgnoreCase(searchCategory)) {
            searchCategory = "money";
        }
        digitalProductRepository.updateActiveStatusByCategoryAndIsPostpaid(searchCategory, cat.getIsPostpaid(),
                newStatus);
        return cat;
    }

    @org.springframework.transaction.annotation.Transactional
    public DigitalCategory updateCategory(Long id, Map<String, Object> payload) {
        DigitalCategory cat = digitalCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Digital category not found for ID: " + id));

        if (payload.containsKey("name") && payload.get("name") != null) {
            cat.setName(payload.get("name").toString().trim());
        }
        if (payload.containsKey("iconName")) {
            cat.setIconName(payload.get("iconName") != null ? payload.get("iconName").toString().trim() : null);
        }
        if (payload.containsKey("iconUrl")) {
            cat.setIconUrl(payload.get("iconUrl") != null ? payload.get("iconUrl").toString().trim() : null);
        }
        if (payload.containsKey("colorHex")) {
            cat.setColorHex(payload.get("colorHex") != null ? payload.get("colorHex").toString().trim() : null);
        }
        if (payload.containsKey("isActive") && payload.get("isActive") != null) {
            boolean active = Boolean.parseBoolean(payload.get("isActive").toString());
            cat.setIsActive(active);

            String searchCategory = cat.getCode();
            if ("emoney".equalsIgnoreCase(searchCategory) || "e-money".equalsIgnoreCase(searchCategory)) {
                searchCategory = "money";
            }
            digitalProductRepository.updateActiveStatusByCategoryAndIsPostpaid(searchCategory, cat.getIsPostpaid(),
                    active);
        }
        if (payload.containsKey("sortOrder") && payload.get("sortOrder") != null) {
            try {
                cat.setSortOrder(Integer.parseInt(payload.get("sortOrder").toString()));
            } catch (Exception ignored) {
            }
        }
        return digitalCategoryRepository.save(cat);
    }

    public DigitalProduct getProductBySku(String sku) {
        return digitalProductRepository.findByBuyerSkuCode(sku)
                .orElseThrow(() -> new RuntimeException("Digital product not found for SKU: " + sku));
    }
}
