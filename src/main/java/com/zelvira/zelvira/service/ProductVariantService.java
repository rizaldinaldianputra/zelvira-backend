package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.ProductVariantRequest;
import com.zelvira.zelvira.dto.ProductVariantResponse;
import com.zelvira.zelvira.entity.Product;
import com.zelvira.zelvira.entity.ProductVariant;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.ProductRepository;
import com.zelvira.zelvira.repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    @Value("${app.minio.public-url:${NEXT_PUBLIC_STORAGE_URL:https://minio.zelvira.co.id}}")
    private String minioPublicUrl;

    public ProductVariantService(ProductVariantRepository productVariantRepository,
            ProductRepository productRepository) {
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
    }

    public ProductVariantResponse createVariant(Long productId, ProductVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(request.getSku())
                .size(request.getSize())
                .color(request.getColor())
                .hexColor(request.getHexColor())
                .groupName(request.getGroupName())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .costPrice(request.getCostPrice())
                .barcode(request.getBarcode())
                .stock(request.getStock() != null ? request.getStock() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .imageUrl(request.getImageUrl())
                .build();

        ProductVariant savedVariant = productVariantRepository.save(variant);
        return mapToResponse(savedVariant);
    }

    public ProductVariantResponse updateVariant(Long productId, Long variantId, ProductVariantRequest request) {
        ProductVariant variant = getVariantAndVerifyProduct(productId, variantId);

        variant.setSku(request.getSku());
        variant.setSize(request.getSize());
        variant.setColor(request.getColor());
        variant.setHexColor(request.getHexColor());
        variant.setGroupName(request.getGroupName());
        variant.setPrice(request.getPrice());
        variant.setDiscountPrice(request.getDiscountPrice());
        variant.setCostPrice(request.getCostPrice());
        variant.setBarcode(request.getBarcode());
        if (request.getStock() != null)
            variant.setStock(request.getStock());
        if (request.getIsActive() != null)
            variant.setIsActive(request.getIsActive());
        if (request.getImageUrl() != null)
            variant.setImageUrl(request.getImageUrl());

        ProductVariant updatedVariant = productVariantRepository.save(variant);
        return mapToResponse(updatedVariant);
    }

    public ProductVariantResponse getVariantById(Long productId, Long variantId) {
        ProductVariant variant = getVariantAndVerifyProduct(productId, variantId);
        return mapToResponse(variant);
    }

    public List<ProductVariantResponse> getVariantsByProductId(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }
        return productVariantRepository.findByProductIdAndIsActiveTrue(productId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void deleteVariant(Long productId, Long variantId) {
        ProductVariant variant = getVariantAndVerifyProduct(productId, variantId);
        boolean isReferenced = productVariantRepository.isVariantReferenced(variantId);
        if (isReferenced) {
            // Soft-deactivate if referenced
            variant.setIsActive(false);
            variant.setStock(0);
            productVariantRepository.save(variant);
        } else {
            productVariantRepository.delete(variant);
        }
    }

    private ProductVariant getVariantAndVerifyProduct(Long productId, Long variantId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Variant not found with id: " + variantId));
        if (!variant.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Variant does not belong to the specified product");
        }
        return variant;
    }

    private ProductVariantResponse mapToResponse(ProductVariant variant) {
        return ProductVariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProduct().getId())
                .sku(variant.getSku())
                .size(variant.getSize())
                .color(variant.getColor())
                .hexColor(variant.getHexColor())
                .groupName(variant.getGroupName())
                .price(variant.getPrice())
                .discountPrice(variant.getDiscountPrice())
                .costPrice(variant.getCostPrice())
                .barcode(variant.getBarcode())
                .stock(variant.getStock() != null ? variant.getStock() : 0)
                .isActive(variant.getIsActive())
                .imageUrl(formatImageUrl(variant.getImageUrl()))
                .createdAt(variant.getCreatedAt())
                .updatedAt(variant.getUpdatedAt())
                .build();
    }

    private String formatImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.startsWith("data:")) {
            return url;
        }
        String cleanPublicUrl = minioPublicUrl != null && !minioPublicUrl.isBlank()
                ? (minioPublicUrl.endsWith("/") ? minioPublicUrl.substring(0, minioPublicUrl.length() - 1)
                        : minioPublicUrl)
                : "https://minio.zelvira.co.id";

        String[] internalEndpoints = new String[] {
                "http://zelixa-minio:9000",
                "http://zelvira-minio:9000",
                "http://minio:9000",
                "http://localhost:9000",
                "https://storage.zelvira.co.id"
        };
        for (String endpoint : internalEndpoints) {
            if (url.startsWith(endpoint)) {
                return cleanPublicUrl + url.substring(endpoint.length());
            }
        }

        if (url.startsWith("/")) {
            return cleanPublicUrl + url;
        }

        return url;
    }
}
