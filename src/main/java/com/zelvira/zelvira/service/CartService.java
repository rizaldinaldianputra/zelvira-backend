package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.CartItemRequest;
import com.zelvira.zelvira.dto.CartItemResponse;
import com.zelvira.zelvira.entity.CartItem;
import com.zelvira.zelvira.entity.ProductImage;
import com.zelvira.zelvira.entity.ProductVariant;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.CartItemRepository;
import com.zelvira.zelvira.repository.ProductImageRepository;
import com.zelvira.zelvira.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;

    public CartService(CartItemRepository cartItemRepository,
            ProductVariantRepository productVariantRepository,
            ProductImageRepository productImageRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
    }

    public List<CartItemResponse> getCart(User user) {
        return cartItemRepository.findByUser(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CartItemResponse addToCart(User user, CartItemRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Jumlah pesanan minimal 1");
        }

        ProductVariant variant = productVariantRepository.findById(request.getProductVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));

        int availableStock = variant.getStock() != null ? variant.getStock() : 0;
        if (availableStock <= 0) {
            throw new IllegalArgumentException("Stok produk tidak tersedia (habis)");
        }

        if (request.getQuantity() > availableStock) {
            throw new IllegalArgumentException(
                    "Jumlah pesanan melebihi stok yang tersedia (tersedia: " + availableStock + ")");
        }

        CartItem existingCartItem = cartItemRepository.findByUserAndProductVariant(user, variant).orElse(null);
        if (existingCartItem != null) {
            int newTotalQty = existingCartItem.getQuantity() + request.getQuantity();
            if (newTotalQty > availableStock) {
                throw new IllegalArgumentException("Total di keranjang (" + newTotalQty
                        + ") melebihi stok yang tersedia (" + availableStock + ")");
            }
            existingCartItem.setQuantity(newTotalQty);
            return mapToResponse(cartItemRepository.save(existingCartItem));
        } else {
            CartItem cartItem = CartItem.builder()
                    .user(user)
                    .productVariant(variant)
                    .quantity(request.getQuantity())
                    .build();
            return mapToResponse(cartItemRepository.save(cartItem));
        }
    }

    @Transactional
    public CartItemResponse updateQuantity(User user, Long cartItemId, Integer quantity) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (quantity == null || quantity <= 0) {
            cartItemRepository.delete(cartItem);
            return null;
        }

        ProductVariant variant = cartItem.getProductVariant();
        int availableStock = variant.getStock() != null ? variant.getStock() : 0;
        if (quantity > availableStock) {
            throw new IllegalArgumentException("Jumlah melebihi stok yang tersedia (tersedia: " + availableStock + ")");
        }

        cartItem.setQuantity(quantity);
        return mapToResponse(cartItemRepository.save(cartItem));
    }

    @Transactional
    public void removeFromCart(User user, Long cartItemId) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        cartItemRepository.delete(cartItem);
    }

    @Transactional
    public void clearCart(User user) {
        cartItemRepository.deleteByUser(user);
    }

    private CartItemResponse mapToResponse(CartItem cartItem) {
        ProductVariant variant = cartItem.getProductVariant();

        CartItemResponse response = CartItemResponse.builder()
                .id(cartItem.getId())
                .productId(variant.getProduct().getId())
                .productName(variant.getProduct().getName())
                .productVariantId(variant.getId())
                .size(variant.getSize())
                .color(variant.getColor())
                .groupName(variant.getGroupName())
                .price(variant.getPrice())
                .discountPrice(variant.getDiscountPrice())
                .quantity(cartItem.getQuantity())
                .stock(variant.getStock() != null ? variant.getStock() : 0)
                .build();

        // Get image - try variant image first, then product thumbnail, then image repository
        if (variant.getImageUrl() != null && !variant.getImageUrl().isBlank()) {
            response.setImageUrl(variant.getImageUrl());
        } else if (variant.getProduct() != null && variant.getProduct().getThumbnailUrl() != null && !variant.getProduct().getThumbnailUrl().isBlank()) {
            response.setImageUrl(variant.getProduct().getThumbnailUrl());
        } else {
            productImageRepository.findByProductId(variant.getProduct().getId()).stream()
                    .filter(img -> variant.getId().equals(img.getVariantId()))
                    .findFirst()
                    .ifPresentOrElse(
                            img -> response.setImageUrl(img.getUrl()),
                            () -> productImageRepository.findByProductId(variant.getProduct().getId()).stream()
                                    .filter(img -> img.getVariantId() == null)
                                    .findFirst()
                                    .ifPresent(img -> response.setImageUrl(img.getUrl())));
        }

        return response;
    }
}
