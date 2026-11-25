package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.WishlistItemResponse;
import com.zelvira.zelvira.entity.Product;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.entity.WishlistItem;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.ProductImageRepository;
import com.zelvira.zelvira.repository.ProductRepository;
import com.zelvira.zelvira.repository.WishlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    public WishlistService(WishlistItemRepository wishlistRepository, ProductRepository productRepository,
            ProductImageRepository productImageRepository) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
    }

    public List<WishlistItemResponse> getWishlist(User user) {
        return wishlistRepository.findByUser(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void toggleWishlist(User user, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        wishlistRepository.findByUserAndProduct(user, product).ifPresentOrElse(
                wishlistRepository::delete,
                () -> wishlistRepository.save(WishlistItem.builder()
                        .user(user)
                        .product(product)
                        .build()));
    }

    @Transactional
    public void removeFromWishlist(User user, Long wishlistId) {
        WishlistItem wishlistItem = wishlistRepository.findById(wishlistId)
                .filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found"));

        wishlistRepository.delete(wishlistItem);
    }

    private WishlistItemResponse mapToResponse(WishlistItem wishlistItem) {
        Product product = wishlistItem.getProduct();

        WishlistItemResponse response = WishlistItemResponse.builder()
                .id(wishlistItem.getId())
                .productId(product.getId())
                .productName(product.getName())
                .slug(product.getSlug())
                .build();

        // Get default price from first variant if available
        if (!product.getVariants().isEmpty()) {
            response.setPrice(product.getVariants().get(0).getPrice());
        }

        // Get image
        if (product.getThumbnailUrl() != null && !product.getThumbnailUrl().isBlank()) {
            response.setImageUrl(product.getThumbnailUrl());
        } else {
            productImageRepository.findByProductId(product.getId()).stream()
                    .filter(img -> img.getVariantId() == null)
                    .findFirst()
                    .ifPresent(img -> response.setImageUrl(img.getUrl()));
        }

        return response;
    }
}
