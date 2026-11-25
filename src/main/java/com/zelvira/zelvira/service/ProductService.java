package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.ProductRequest;
import com.zelvira.zelvira.dto.ProductResponse;
import com.zelvira.zelvira.entity.Product;
import com.zelvira.zelvira.entity.ProductImage;
import com.zelvira.zelvira.entity.ProductVariant;
import com.zelvira.zelvira.entity.StockTransaction;
import com.zelvira.zelvira.util.SlugUtils;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.ProductImageRepository;
import com.zelvira.zelvira.repository.ProductRepository;
import com.zelvira.zelvira.repository.ProductVariantRepository;
import com.zelvira.zelvira.repository.StockTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final StockTransactionRepository transactionRepository;
    private final com.zelvira.zelvira.repository.CartItemRepository cartItemRepository;
    private final com.zelvira.zelvira.repository.ReviewRepository reviewRepository;

    private final List<MarketplaceService> marketplaceServices;

    @Value("${app.minio.public-url:${NEXT_PUBLIC_STORAGE_URL:https://minio.zelvira.my.id}}")
    private String minioPublicUrl;

    public ProductService(ProductRepository productRepository, ProductVariantRepository variantRepository,
            ProductImageRepository imageRepository, StockTransactionRepository transactionRepository,
            com.zelvira.zelvira.repository.CartItemRepository cartItemRepository,
            com.zelvira.zelvira.repository.ReviewRepository reviewRepository,
            List<MarketplaceService> marketplaceServices) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.imageRepository = imageRepository;
        this.transactionRepository = transactionRepository;
        this.cartItemRepository = cartItemRepository;
        this.reviewRepository = reviewRepository;
        this.marketplaceServices = marketplaceServices;
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String slug = request.getSlug();
        if (slug == null || slug.trim().isEmpty()) {
            slug = SlugUtils.makeSlug(request.getName());
        }

        // Ensure uniqueness
        if (productRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 5);
        }

        String masterSku = request.getSku();
        if (masterSku == null || masterSku.trim().isEmpty()) {
            masterSku = "SKU-" + slug.toUpperCase();
        } else {
            masterSku = masterSku.trim().toUpperCase();
        }

        String thumbnail = request.getThumbnailUrl();
        if (thumbnail == null || thumbnail.isBlank()) {
            thumbnail = request.getImageUrl();
        }

        Product product = Product.builder()
                .name(request.getName())
                .slug(slug)
                .sku(masterSku)
                .description(request.getDescription())
                .shortDescription(request.getShortDescription())
                .categoryId(request.getCategoryId())
                .brandId(request.getBrandId())
                .gender(request.getGender())
                .material(request.getMaterial())
                .weight(request.getWeight())
                .pointsEarned(request.getPointsEarned() != null ? request.getPointsEarned() : 0L)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .thumbnailUrl(thumbnail)
                .build();

        Product savedProduct = productRepository.save(product);

        // Handle Variants if provided
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            int vIdx = 1;
            for (com.zelvira.zelvira.dto.ProductVariantRequest vReq : request.getVariants()) {
                String variantSku = vReq.getSku();
                if (variantSku == null || variantSku.trim().isEmpty()) {
                    String colorPart = vReq.getColor() != null && !vReq.getColor().isBlank() ? vReq.getColor().trim()
                            .substring(0, Math.min(3, vReq.getColor().trim().length())).toUpperCase() : "DEF";
                    String sizePart = vReq.getSize() != null && !vReq.getSize().isBlank() ? vReq.getSize().trim()
                            .substring(0, Math.min(3, vReq.getSize().trim().length())).toUpperCase() : "ALL";
                    variantSku = masterSku + "-" + colorPart + "-" + sizePart + "-" + vIdx;
                }
                vIdx++;

                ProductVariant variant = ProductVariant.builder()
                        .product(savedProduct)
                        .sku(variantSku.trim().toUpperCase())
                        .size(vReq.getSize())
                        .color(vReq.getColor())
                        .hexColor(vReq.getHexColor())
                        .groupName(vReq.getGroupName())
                        .price(vReq.getPrice())
                        .discountPrice(vReq.getDiscountPrice())
                        .costPrice(vReq.getCostPrice())
                        .barcode(vReq.getBarcode())
                        .stock(vReq.getStock() != null ? vReq.getStock() : 0)
                        .isActive(vReq.getIsActive() != null ? vReq.getIsActive() : true)
                        .imageUrl(vReq.getImageUrl())
                        .build();
                ProductVariant savedVariant = variantRepository.save(variant);

                // Initial Stock Transaction
                if (savedVariant.getStock() > 0) {
                    transactionRepository.save(StockTransaction.builder()
                            .variant(savedVariant)
                            .quantity(savedVariant.getStock())
                            .type("INITIAL")
                            .notes("Initial stock on creation")
                            .build());
                }

                // Save variant image in image repository if provided
                if (vReq.getImageUrl() != null && !vReq.getImageUrl().isEmpty()) {
                    ProductImage vImg = ProductImage.builder()
                            .product(savedProduct)
                            .variantId(savedVariant.getId())
                            .url(vReq.getImageUrl())
                            .sortOrder(0)
                            .build();
                    imageRepository.save(vImg);
                }
            }
        } else {
            // Create Default Variant if none provided (legacy compatibility)
            ProductVariant variant = ProductVariant.builder()
                    .product(savedProduct)
                    .sku(masterSku)
                    .groupName("Default")
                    .price(request.getPrice())
                    .stock(request.getQty() != null ? request.getQty() : 0)
                    .isActive(true)
                    .imageUrl(thumbnail)
                    .build();
            ProductVariant savedVariant = variantRepository.save(variant);

            // Initial Stock Transaction
            if (savedVariant.getStock() > 0) {
                transactionRepository.save(StockTransaction.builder()
                        .variant(savedVariant)
                        .quantity(savedVariant.getStock())
                        .type("INITIAL")
                        .notes("Initial stock on creation (default variant)")
                        .build());
            }
        }

        // Handle Product Media: Thumbnail (sortOrder 0) and Banners (sortOrder 1, 2, ...)
        int sortOrder = 0;
        if (thumbnail != null && !thumbnail.isBlank()) {
            imageRepository.save(ProductImage.builder()
                    .product(savedProduct)
                    .url(thumbnail)
                    .sortOrder(sortOrder++)
                    .build());
        }

        List<String> bannerList = request.getBanners() != null && !request.getBanners().isEmpty()
                ? request.getBanners()
                : request.getImages();

        if (bannerList != null && !bannerList.isEmpty()) {
            for (String url : bannerList) {
                if (url == null || url.isBlank())
                    continue;
                // Avoid duplicating thumbnail if it is also in the banner list
                if (thumbnail != null && url.equals(thumbnail))
                    continue;

                imageRepository.save(ProductImage.builder()
                        .product(savedProduct)
                        .url(url)
                        .sortOrder(sortOrder++)
                        .build());
            }
        }

        ProductResponse resp = mapToResponse(savedProduct);
        return resp;
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setShortDescription(request.getShortDescription());
        product.setCategoryId(request.getCategoryId());
        product.setBrandId(request.getBrandId());
        product.setGender(request.getGender());
        product.setMaterial(request.getMaterial());
        product.setWeight(request.getWeight());
        if (request.getSku() != null && !request.getSku().isBlank()) {
            product.setSku(request.getSku().trim().toUpperCase());
        }
        if (request.getPointsEarned() != null)
            product.setPointsEarned(request.getPointsEarned());
        if (request.getIsActive() != null)
            product.setIsActive(request.getIsActive());

        String thumbnail = request.getThumbnailUrl();
        if (thumbnail == null || thumbnail.isBlank()) {
            thumbnail = request.getImageUrl();
        }
        if (thumbnail != null) {
            product.setThumbnailUrl(thumbnail);
        }

        Product updatedProduct = productRepository.save(product);

        // Intelligent Merge for Variants
        List<ProductVariant> existingVariants = variantRepository.findByProductId(id);
        List<com.zelvira.zelvira.dto.ProductVariantRequest> incomingVariants = request.getVariants() != null
                ? request.getVariants()
                : new java.util.ArrayList<>();

        // 1. Resolve IDs for incoming variants if missing by matching existing SKU or (GroupName + Color + Size)
        for (com.zelvira.zelvira.dto.ProductVariantRequest vReq : incomingVariants) {
            if (vReq.getId() == null) {
                existingVariants.stream()
                        .filter(v -> (vReq.getSku() != null && !vReq.getSku().isBlank() && vReq.getSku().equalsIgnoreCase(v.getSku()))
                                || (java.util.Objects.equals(vReq.getGroupName(), v.getGroupName())
                                        && java.util.Objects.equals(vReq.getColor(), v.getColor())
                                        && java.util.Objects.equals(vReq.getSize(), v.getSize())))
                        .findFirst()
                        .ifPresent(v -> vReq.setId(v.getId()));
            }
        }

        // 2. Identify variants to delete
        java.util.Set<Long> incomingIds = incomingVariants.stream()
                .map(v -> v.getId())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        List<ProductVariant> toDelete = existingVariants.stream()
                .filter(v -> !incomingIds.contains(v.getId()))
                .collect(java.util.stream.Collectors.toList());

        for (ProductVariant v : toDelete) {
            boolean isReferenced = variantRepository.isVariantReferenced(v.getId());
            if (isReferenced) {
                // Cannot hard-delete because of historical/operational references (orders, flash sales, PO, etc.)
                // Safely soft-deactivate and clear stock
                v.setIsActive(false);
                v.setStock(0);
                variantRepository.save(v);
            } else {
                // No operational/financial references, safe to hard-delete
                cartItemRepository.deleteByProductVariantId(v.getId());
                transactionRepository.deleteByVariantId(v.getId());
                imageRepository.deleteByProductIdAndVariantId(id, v.getId());
                variantRepository.delete(v);
            }
        }
        variantRepository.flush(); // CRITICAL: Flush changes

        // 2. Update or Create variants
        for (com.zelvira.zelvira.dto.ProductVariantRequest vReq : incomingVariants) {
            ProductVariant variant;
            if (vReq.getId() != null) {
                variant = existingVariants.stream()
                        .filter(v -> v.getId().equals(vReq.getId()))
                        .findFirst()
                        .orElse(new ProductVariant());
            } else {
                variant = new ProductVariant();
            }

            boolean isNewVariant = (variant.getId() == null);
            int oldStock = variant.getStock() != null ? variant.getStock() : 0;
            int newStock = vReq.getStock() != null ? vReq.getStock() : 0;

            variant.setProduct(updatedProduct);
            variant.setSku(vReq.getSku());
            variant.setSize(vReq.getSize());
            variant.setColor(vReq.getColor());
            variant.setHexColor(vReq.getHexColor());
            variant.setGroupName(vReq.getGroupName());
            variant.setPrice(vReq.getPrice());
            variant.setDiscountPrice(vReq.getDiscountPrice());
            variant.setCostPrice(vReq.getCostPrice());
            variant.setBarcode(vReq.getBarcode());
            variant.setStock(newStock);
            variant.setIsActive(vReq.getIsActive() != null ? vReq.getIsActive() : true);
            variant.setImageUrl(vReq.getImageUrl());

            ProductVariant savedVariant = variantRepository.save(variant);

            // Record stock transactions
            if (isNewVariant && savedVariant.getStock() > 0) {
                transactionRepository.save(StockTransaction.builder()
                        .variant(savedVariant)
                        .quantity(savedVariant.getStock())
                        .type("INITIAL")
                        .notes("Initial stock for new variant during update")
                        .build());
            } else if (!isNewVariant && oldStock != newStock) {
                int diff = newStock - oldStock;
                transactionRepository.save(StockTransaction.builder()
                        .variant(savedVariant)
                        .quantity(diff)
                        .type("ADJUSTMENT")
                        .notes("Stock adjusted during product update")
                        .build());
            }

            // Handle Variant Image
            imageRepository.deleteByProductIdAndVariantId(id, savedVariant.getId());
            if (vReq.getImageUrl() != null && !vReq.getImageUrl().isEmpty()) {
                imageRepository.save(ProductImage.builder()
                        .product(updatedProduct)
                        .variantId(savedVariant.getId())
                        .url(vReq.getImageUrl())
                        .sortOrder(0)
                        .build());
            }
        }

        // 3. Handle Product Media: Thumbnail & Banners
        imageRepository.deleteByProductIdAndVariantIdIsNull(id);
        int sortOrder = 0;
        String finalThumbnail = updatedProduct.getThumbnailUrl();
        if (finalThumbnail != null && !finalThumbnail.isBlank()) {
            imageRepository.save(ProductImage.builder()
                    .product(updatedProduct)
                    .url(finalThumbnail)
                    .sortOrder(sortOrder++)
                    .build());
        }

        List<String> bannerList = request.getBanners() != null && !request.getBanners().isEmpty()
                ? request.getBanners()
                : request.getImages();

        if (bannerList != null && !bannerList.isEmpty()) {
            for (String url : bannerList) {
                if (url == null || url.isBlank())
                    continue;
                if (finalThumbnail != null && url.equals(finalThumbnail))
                    continue;

                imageRepository.save(ProductImage.builder()
                        .product(updatedProduct)
                        .url(url)
                        .sortOrder(sortOrder++)
                        .build());
            }
        }

        // Auto-sync product details (name, description, images) to connected
        // marketplaces
        if (marketplaceServices != null && !marketplaceServices.isEmpty()) {
            final Product prodToSync = updatedProduct;
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                for (MarketplaceService ms : marketplaceServices) {
                    try {
                        ms.updateProduct(prodToSync);
                    } catch (Exception e) {
                        log.warn("Auto-syncing updated product {} to marketplace {} failed: {}",
                                prodToSync.getId(), ms.getMarketplaceType(), e.getMessage());
                    }
                }
            });
        }

        ProductResponse resp = mapToResponse(updatedProduct);
        return resp;
    }

    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToResponse(product);
    }

    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with slug: " + slug));
        return mapToResponse(product);
    }

    public Page<ProductResponse> getAllProducts(int page, int size, String search, Long categoryId) {
        Pageable pageable = PageRequest.of(page, size);

        // 1. Try Category + Search or Category only
        if (categoryId != null) {
            if (search != null && !search.trim().isEmpty()) {
                return productRepository.findByCategoryIdAndNameContainingIgnoreCase(categoryId, search, pageable)
                        .map(this::mapToResponse);
            } else {
                return productRepository.findByCategoryId(categoryId, pageable).map(this::mapToResponse);
            }
        }

        // 2. Try General Search
        if (search != null && !search.trim().isEmpty()) {
            return productRepository.searchProducts(search, pageable).map(this::mapToResponse);
        }

        // 3. Just All Products
        return productRepository.findAll(pageable).map(this::mapToResponse);
    }

    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::mapToResponse);
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        productRepository.delete(product);
    }

    public List<ProductResponse> getProductsByIds(List<Long> ids) {
        return productRepository.findByIdIn(ids).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .sku(product.getSku())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .categoryId(product.getCategoryId())
                .brandId(product.getBrandId())
                .gender(product.getGender())
                .material(product.getMaterial())
                .weight(product.getWeight())
                .pointsEarned(product.getPointsEarned())
                .isActive(product.getIsActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();

        if (product.getCategory() != null) {
            response.setCategory(com.zelvira.zelvira.dto.CategoryResponse.builder()
                    .id(product.getCategory().getId())
                    .name(product.getCategory().getName())
                    .slug(product.getCategory().getSlug())
                    .imageUrl(formatImageUrl(product.getCategory().getImageUrl()))
                    .build());
        }

        if (product.getBrand() != null) {
            response.setBrand(com.zelvira.zelvira.dto.BrandResponse.builder()
                    .id(product.getBrand().getId())
                    .name(product.getBrand().getName())
                    .slug(product.getBrand().getSlug())
                    .logoUrl(formatImageUrl(product.getBrand().getLogoUrl()))
                    .build());
        }

        List<ProductVariant> variants = variantRepository.findByProductIdAndIsActiveTrue(product.getId());
        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(product.getId());

        if (!variants.isEmpty()) {
            response.setPrice(variants.get(0).getPrice());
            response.setQty(variants.get(0).getStock());

            response.setVariants(variants.stream().map(v -> {
                com.zelvira.zelvira.dto.ProductVariantResponse vRes = com.zelvira.zelvira.dto.ProductVariantResponse
                        .builder()
                        .id(v.getId())
                        .productId(v.getProduct().getId())
                        .sku(v.getSku())
                        .size(v.getSize())
                        .color(v.getColor())
                        .hexColor(v.getHexColor())
                        .groupName(v.getGroupName())
                        .price(v.getPrice())
                        .discountPrice(v.getDiscountPrice())
                        .costPrice(v.getCostPrice())
                        .barcode(v.getBarcode())
                        .stock(v.getStock())
                        .isActive(v.getIsActive())
                        .createdAt(v.getCreatedAt())
                        .updatedAt(v.getUpdatedAt())
                        .build();

                // Find image for this variant: use direct variant imageUrl first, fallback to product image table
                String variantImgUrl = v.getImageUrl();
                if (variantImgUrl == null || variantImgUrl.isBlank()) {
                    variantImgUrl = images.stream()
                            .filter(img -> v.getId().equals(img.getVariantId()))
                            .findFirst()
                            .map(ProductImage::getUrl)
                            .orElse(null);
                }
                vRes.setImageUrl(formatImageUrl(variantImgUrl));

                return vRes;
            }).collect(java.util.stream.Collectors.toList()));
        }

        List<ProductImage> generalImages = images.stream()
                .filter(img -> img.getVariantId() == null)
                .collect(java.util.stream.Collectors.toList());

        // Resolve thumbnail for product cards
        String rawThumbnail = product.getThumbnailUrl();
        if (rawThumbnail == null || rawThumbnail.isBlank()) {
            if (!generalImages.isEmpty()) {
                rawThumbnail = generalImages.get(0).getUrl();
            } else if (!images.isEmpty()) {
                rawThumbnail = images.get(0).getUrl();
            }
        }
        String formattedThumbnail = formatImageUrl(rawThumbnail);
        response.setThumbnailUrl(formattedThumbnail);
        response.setThumbnail(formattedThumbnail);
        response.setImageUrl(formattedThumbnail);

        // Resolve banners for product detail slider
        List<String> bannerUrls = generalImages.stream()
                .map(img -> formatImageUrl(img.getUrl()))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

        if (bannerUrls.isEmpty() && formattedThumbnail != null) {
            bannerUrls = java.util.List.of(formattedThumbnail);
        }
        response.setBanners(bannerUrls);
        response.setImages(bannerUrls);

        // Calculate real rating and review count from reviews
        try {
            java.util.List<com.zelvira.zelvira.entity.Review> activeReviews = reviewRepository
                    .findByProductIdAndIsActiveTrue(product.getId());
            if (activeReviews != null && !activeReviews.isEmpty()) {
                double avg = activeReviews.stream()
                        .mapToInt(com.zelvira.zelvira.entity.Review::getRating)
                        .average()
                        .orElse(0.0);
                // Round to 1 decimal place
                response.setAverageRating(Math.round(avg * 10.0) / 10.0);
                response.setReviewCount(activeReviews.size());
            } else {
                response.setAverageRating(0.0);
                response.setReviewCount(0);
            }
        } catch (Exception e) {
            response.setAverageRating(0.0);
            response.setReviewCount(0);
        }

        return response;
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
                : "https://minio.zelvira.my.id";

        String[] internalEndpoints = new String[] {
                "http://zelixa-minio:9000",
                "http://zelvira-minio:9000",
                "http://minio:9000",
                "http://localhost:9000",
                "https://storage.zelvira.my.id",
                "https://storage.zelvira.my.id"
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
