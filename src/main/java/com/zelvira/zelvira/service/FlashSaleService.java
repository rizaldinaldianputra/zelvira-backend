package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.*;
import com.zelvira.zelvira.entity.*;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FlashSaleService {

    private final FlashSaleRepository flashSaleRepository;
    private final FlashSaleItemRepository flashSaleItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public FlashSaleService(FlashSaleRepository flashSaleRepository, FlashSaleItemRepository flashSaleItemRepository,
            ProductRepository productRepository, ProductVariantRepository productVariantRepository) {
        this.flashSaleRepository = flashSaleRepository;
        this.flashSaleItemRepository = flashSaleItemRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Transactional
    public FlashSaleResponse createFlashSale(FlashSaleRequest request) {
        FlashSale flashSale = FlashSale.builder()
                .name(request.getName())
                .platform(request.getPlatform() != null ? request.getPlatform() : "WEB")
                .bannerUrl(request.getBannerUrl())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .maxPurchasePerUser(request.getMaxPurchasePerUser())
                .build();

        FlashSale savedFlashSale = flashSaleRepository.save(flashSale);

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            List<FlashSaleItem> items = buildItems(request.getItems(), savedFlashSale);
            flashSaleItemRepository.saveAll(items);
            savedFlashSale.setItems(items);
        }

        return mapToResponse(savedFlashSale);
    }

    public List<FlashSaleResponse> getAllFlashSales() {
        return flashSaleRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<FlashSaleResponse> getAllFlashSalesByPlatform(String platform) {
        return flashSaleRepository.findByPlatformOrderByCreatedAtDesc(platform).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<FlashSaleResponse> getActiveFlashSales() {
        return flashSaleRepository.findActiveFlashSales(LocalDateTime.now()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<FlashSaleResponse> getActiveFlashSalesByPlatform(String platform) {
        return flashSaleRepository.findActiveFlashSalesByPlatform(platform, LocalDateTime.now()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public FlashSaleResponse getFlashSaleById(Long id) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale not found with id: " + id));
        return mapToResponse(flashSale);
    }

    @Transactional
    public FlashSaleResponse updateFlashSale(Long id, FlashSaleRequest request) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale not found with id: " + id));

        flashSale.setName(request.getName());
        if (request.getPlatform() != null)
            flashSale.setPlatform(request.getPlatform());
        if (request.getBannerUrl() != null)
            flashSale.setBannerUrl(request.getBannerUrl());
        flashSale.setStartTime(request.getStartTime());
        flashSale.setEndTime(request.getEndTime());
        flashSale.setIsActive(request.getIsActive());
        if (request.getMaxPurchasePerUser() != null)
            flashSale.setMaxPurchasePerUser(request.getMaxPurchasePerUser());

        if (request.getItems() != null) {
            flashSaleItemRepository.deleteByFlashSaleId(id);
            List<FlashSaleItem> items = buildItems(request.getItems(), flashSale);
            flashSaleItemRepository.saveAll(items);
            flashSale.setItems(items);
        }

        FlashSale updatedFlashSale = flashSaleRepository.save(flashSale);
        return mapToResponse(updatedFlashSale);
    }

    @Transactional
    public void deleteFlashSale(Long id) {
        if (!flashSaleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Flash sale not found with id: " + id);
        }
        flashSaleRepository.deleteById(id);
    }

    private List<FlashSaleItem> buildItems(List<FlashSaleItemRequest> itemRequests, FlashSale flashSale) {
        return itemRequests.stream().map(itemReq -> {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.getProductId()));

            ProductVariant variant = productVariantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + itemReq.getVariantId()));

            // Validate: flash price must be less than original price
            BigDecimal originalPrice = variant.getPrice();
            if (originalPrice != null && itemReq.getDiscountPrice() != null
                    && itemReq.getDiscountPrice().compareTo(originalPrice) >= 0) {
                throw new IllegalArgumentException(
                        "Harga Flash Sale harus lebih murah dari harga normal (" + originalPrice + ") untuk produk: "
                                + product.getName());
            }

            return FlashSaleItem.builder()
                    .flashSale(flashSale)
                    .product(product)
                    .productVariant(variant)
                    .discountPrice(itemReq.getDiscountPrice())
                    .stockLimit(itemReq.getStockLimit())
                    .flashStock(itemReq.getStockLimit())
                    .soldCount(0)
                    .maxPurchasePerUser(itemReq.getMaxPurchasePerUser())
                    .build();
        }).collect(Collectors.toList());
    }

    private FlashSaleResponse mapToResponse(FlashSale flashSale) {
        return FlashSaleResponse.builder()
                .id(flashSale.getId())
                .name(flashSale.getName())
                .platform(flashSale.getPlatform())
                .bannerUrl(flashSale.getBannerUrl())
                .startTime(flashSale.getStartTime())
                .endTime(flashSale.getEndTime())
                .isActive(flashSale.getIsActive())
                .maxPurchasePerUser(flashSale.getMaxPurchasePerUser())
                .createdAt(flashSale.getCreatedAt())
                .items(flashSale.getItems() != null ? flashSale.getItems().stream()
                        .map(item -> {
                            String imageUrl = null;
                            if (item.getProduct().getImages() != null && !item.getProduct().getImages().isEmpty()) {
                                imageUrl = item.getProduct().getImages().get(0).getUrl();
                            }
                            return FlashSaleItemResponse.builder()
                                    .id(item.getId())
                                    .productId(item.getProduct().getId())
                                    .productName(item.getProduct().getName())
                                    .productImageUrl(imageUrl)
                                    .variantId(item.getProductVariant().getId())
                                    .variantName(item.getProductVariant().getSku())
                                    .originalPrice(item.getProductVariant().getPrice())
                                    .discountPrice(item.getDiscountPrice())
                                    .stockLimit(item.getStockLimit())
                                    .flashStock(item.getFlashStock())
                                    .soldCount(item.getSoldCount())
                                    .maxPurchasePerUser(item.getMaxPurchasePerUser())
                                    .build();
                        })
                        .collect(Collectors.toList()) : null)
                .build();
    }
}
