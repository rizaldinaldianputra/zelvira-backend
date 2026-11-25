package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ProductImageRequest;
import com.zelvira.zelvira.dto.ProductImageResponse;
import com.zelvira.zelvira.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService imageService;

    public ProductImageController(ProductImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    public ResponseEntity<ProductImageResponse> addImageToProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ProductImageRequest request) {
        return new ResponseEntity<>(imageService.addImageToProduct(productId, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ProductImageResponse>> getImagesByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(imageService.getImagesByProductId(productId));
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<ProductImageResponse> getImageById(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        return ResponseEntity.ok(imageService.getImageById(productId, imageId));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        imageService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
