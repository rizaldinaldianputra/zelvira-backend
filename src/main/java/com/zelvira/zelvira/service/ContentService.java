package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.ContentItemRequest;
import com.zelvira.zelvira.dto.ContentItemResponse;
import com.zelvira.zelvira.dto.ProductResponse;
import com.zelvira.zelvira.entity.ContentItem;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.ContentItemRepository;
import com.zelvira.zelvira.repository.ContentSectionRepository;
import com.zelvira.zelvira.entity.ContentSection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class ContentService {

    private final ContentItemRepository itemRepository;
    private final ContentSectionRepository sectionRepository;
    private final ProductService productService;

    public ContentService(ContentItemRepository itemRepository, ContentSectionRepository sectionRepository,
            ProductService productService) {
        this.itemRepository = itemRepository;
        this.sectionRepository = sectionRepository;
        this.productService = productService;
    }

    @Value("${app.minio.public-url:${app.minio.url:https://minio.zelvira.co.id}}")
    private String storageUrl;

    @Transactional
    public ContentItemResponse createItem(ContentItemRequest request) {
        ContentItem item = ContentItem.builder()
                .type(request.getType())
                .platform(request.getPlatform() != null ? request.getPlatform() : "WEB")
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .title(request.getTitle())
                .subtitle(request.getSubtitle())
                .imageUrl(request.getImageUrl())
                .linkUrl(request.getLinkUrl())
                .productId(request.getProductId())
                .tag(request.getTag())
                .ctaText(request.getCtaText())
                .badgeText(request.getBadgeText())
                .iconName(request.getIconName())
                .emoji(request.getEmoji())
                .styleConfig(request.getStyleConfig())
                .displayOrder(request.getDisplayOrder())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .contentBody(request.getContentBody())
                .bannerUrl(request.getBannerUrl())
                .productIds(request.getProductIds())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxPurchase(request.getMaxPurchase())
                .soldQty(request.getSoldQty() != null ? request.getSoldQty() : 0)
                .build();

        validateFlashSaleItem(item);

        if (request.getSectionId() != null) {
            ContentSection section = sectionRepository.findById(request.getSectionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Section not found with id: " + request.getSectionId()));
            item.setSection(section);
        }

        return mapToItemResponse(itemRepository.save(item));
    }

    public List<ContentItemResponse> getItemsByType(String type, String platform, boolean activeOnly) {
        List<ContentItem> items;
        if (activeOnly) {
            String targetPlatform = platform != null ? platform : "WEB";
            items = itemRepository.findByTypeAndPlatformAndIsActiveTrueOrderByCreatedAtDesc(type, targetPlatform);
        } else if (platform != null && !platform.isEmpty()) {
            items = itemRepository.findByTypeAndPlatformOrderByCreatedAtDesc(type, platform);
        } else {
            items = itemRepository.findAllByTypeOrderByCreatedAtDesc(type);
        }
        return items.stream().map(this::mapToItemResponse).collect(Collectors.toList());
    }

    public List<ContentItemResponse> getAllItems(String platform) {
        List<ContentItem> items;
        if (platform != null && !platform.isEmpty()) {
            items = itemRepository.findByPlatformOrderByCreatedAtDesc(platform);
        } else {
            items = itemRepository.findAllByOrderByCreatedAtDesc();
        }
        return items.stream().map(this::mapToItemResponse).collect(Collectors.toList());
    }

    @Transactional
    public ContentItemResponse updateItem(Long id, ContentItemRequest request) {
        ContentItem item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Content item not found with id: " + id));

        item.setType(request.getType());
        item.setPlatform(request.getPlatform() != null ? request.getPlatform() : item.getPlatform());
        item.setIsActive(request.getIsActive() != null ? request.getIsActive() : item.getIsActive());
        item.setTitle(request.getTitle());
        item.setSubtitle(request.getSubtitle());
        item.setImageUrl(request.getImageUrl());
        item.setLinkUrl(request.getLinkUrl());
        item.setProductId(request.getProductId());
        item.setTag(request.getTag());
        item.setCtaText(request.getCtaText());
        item.setBadgeText(request.getBadgeText());
        item.setIconName(request.getIconName());
        item.setEmoji(request.getEmoji());
        item.setStyleConfig(request.getStyleConfig());
        item.setDisplayOrder(request.getDisplayOrder());
        item.setStartDate(request.getStartDate());
        item.setEndDate(request.getEndDate());
        item.setContentBody(request.getContentBody());
        item.setBannerUrl(request.getBannerUrl());
        item.setProductIds(request.getProductIds());
        item.setPrice(request.getPrice());
        item.setQuantity(request.getQuantity());
        item.setDiscountType(request.getDiscountType());
        item.setDiscountValue(request.getDiscountValue());
        item.setMaxPurchase(request.getMaxPurchase());
        item.setSoldQty(request.getSoldQty());

        validateFlashSaleItem(item);

        if (request.getSectionId() != null) {
            ContentSection section = sectionRepository.findById(request.getSectionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Section not found with id: " + request.getSectionId()));
            item.setSection(section);
        }

        return mapToItemResponse(itemRepository.save(item));
    }

    @Transactional
    public void deleteItem(Long id) {
        if (!itemRepository.existsById(id)) {
            throw new ResourceNotFoundException("Content item not found with id: " + id);
        }
        itemRepository.deleteById(id);
    }

    public ContentItemResponse getById(String id) {
        ContentItem item = itemRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Content item not found with id: " + id));
        return mapToItemResponse(item);
    }

    public ContentItemResponse mapToItemResponse(ContentItem item) {
        ProductResponse product = null;
        if (item.getProductId() != null) {
            try {
                ProductResponse candidate = productService.getProductById(item.getProductId());
                // Featured/Recommendation rules: inactive or deleted products must not render
                if (Boolean.TRUE.equals(candidate.getIsActive())) {
                    product = candidate;
                }
            } catch (Exception e) {
                // Product might have been deleted
            }
        }

        List<ProductResponse> products = null;
        if (item.getProductIds() != null && !item.getProductIds().trim().isEmpty()) {
            try {
                List<Long> ids = java.util.Arrays.stream(item.getProductIds().split(","))
                        .map(String::trim)
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
                products = productService.getProductsByIds(ids);
            } catch (Exception e) {
                // Ignore if products not found
            }
        }

        return ContentItemResponse.builder()
                .id(item.getId())
                .type(item.getType())
                .platform(item.getPlatform())
                .isActive(item.getIsActive())
                .title(item.getTitle())
                .subtitle(item.getSubtitle())
                .imageUrl(formatImageUrl(item.getImageUrl()))
                .linkUrl(item.getLinkUrl())
                .productId(item.getProductId())
                .tag(item.getTag())
                .ctaText(item.getCtaText())
                .badgeText(item.getBadgeText())
                .iconName(item.getIconName())
                .emoji(item.getEmoji())
                .styleConfig(item.getStyleConfig())
                .displayOrder(item.getDisplayOrder())
                .startDate(item.getStartDate())
                .endDate(item.getEndDate())
                .contentBody(item.getContentBody())
                .bannerUrl(formatImageUrl(item.getBannerUrl()))
                .productIds(item.getProductIds())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .discountType(item.getDiscountType())
                .discountValue(item.getDiscountValue())
                .maxPurchase(item.getMaxPurchase())
                .soldQty(item.getSoldQty())
                .sectionId(item.getSection() != null ? item.getSection().getId() : null)
                .products(products)
                .product(product)
                .build();
    }

    private void validateFlashSaleItem(ContentItem item) {
        if (!"FLASH_SALE_WEB".equals(item.getType()))
            return;

        if (item.getProductId() != null) {
            ProductResponse product = productService.getProductById(item.getProductId());

            // 1. Validation: Price < Normal Price
            double normalPrice = product.getPrice() != null ? product.getPrice().doubleValue() : 0.0;
            double flashPrice = calculateFinalPrice(item, normalPrice);
            if (flashPrice >= normalPrice) {
                throw new IllegalArgumentException(
                        "Harga Flash Sale harus lebih murah dari harga normal (" + normalPrice + ")");
            }

            // 2. Validation: Qty <= Product Stock
            if (item.getQuantity() != null && product.getQty() != null && item.getQuantity() > product.getQty()) {
                throw new IllegalArgumentException(
                        "Stok Flash Sale tidak boleh melebihi stok produk (" + product.getQty() + ")");
            }

            // 3. Validation: No duplicates in same section
            if (item.getSection() != null) {
                boolean duplicate = item.getSection().getItems().stream()
                        .anyMatch(i -> i.getProductId() != null
                                && i.getProductId().equals(item.getProductId())
                                && !i.getId().equals(item.getId()));
                if (duplicate) {
                    throw new IllegalArgumentException("Produk ini sudah ada di dalam sesi Flash Sale yang sama");
                }
            }
        }
    }

    private double calculateFinalPrice(ContentItem item, double originalPrice) {
        if (item.getPrice() != null && item.getPrice() > 0)
            return item.getPrice();
        if (item.getDiscountValue() == null)
            return originalPrice;

        if ("PERCENT".equals(item.getDiscountType())) {
            return originalPrice * (1 - item.getDiscountValue() / 100);
        } else if ("FIXED_AMOUNT".equals(item.getDiscountType())) {
            return Math.max(0, originalPrice - item.getDiscountValue());
        } else {
            return item.getDiscountValue(); // FIXED_PRICE
        }
    }

    private String formatImageUrl(String path) {
        if (path == null || path.trim().isEmpty() || path.startsWith("data:")) {
            return path;
        }

        // Clean storage URL base
        String base = storageUrl;
        if (base.contains("storage.zelvira.co.id")) {
            base = base.replace("storage.zelvira.co.id", "minio.zelvira.co.id");
        } else if (base.contains("storage.zelvira.co.id")) {
            base = base.replace("storage.zelvira.co.id", "minio.zelvira.co.id");
        } else if (base.contains("minio.zelvira.co.id")) {
            base = base.replace("minio.zelvira.co.id", "minio.zelvira.co.id");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        // Clean up legacy, misconfigured, or internal endpoints if they got into the DB
        String cleanPath = path;
        String[] legacyEndpoints = {
                "storage.zelvira.co.id:9000",
                "storage.zelvira.co.id",
                "storage.zelvira.co.id",
                "minio.zelvira.co.id",
                "zelixa-minio:9000",
                "zelvira-minio:9000",
                "minio:9000",
                "localhost:9000",
                "127.0.0.1:9000"
        };
        for (String endpoint : legacyEndpoints) {
            if (cleanPath.contains(endpoint)) {
                cleanPath = cleanPath.substring(cleanPath.indexOf(endpoint) + endpoint.length());
                return base + (cleanPath.startsWith("/") ? cleanPath : "/" + cleanPath);
            }
        }

        if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
            return cleanPath;
        }

        // Ensure it starts with a single slash
        if (!cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }

        return base + cleanPath;
    }
}
