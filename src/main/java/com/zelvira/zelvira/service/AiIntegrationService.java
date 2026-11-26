package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.ai.AiPageResponse;
import com.zelvira.zelvira.dto.ai.AiProductResponse;
import com.zelvira.zelvira.entity.*;
import com.zelvira.zelvira.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AiIntegrationService {

    private static final int MAX_LIMIT = 50;

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final OrganizationRepository organizationRepository;
    private final OrderRepository orderRepository;
    private final EntityManager entityManager;

    @Value("${app.minio.public-url:https://s3.zelvira.co.id}")
    private String publicStorageUrl;

    public AiIntegrationService(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ProductImageRepository imageRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            OrganizationRepository organizationRepository,
            OrderRepository orderRepository,
            EntityManager entityManager) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.imageRepository = imageRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.organizationRepository = organizationRepository;
        this.orderRepository = orderRepository;
        this.entityManager = entityManager;
    }

    public AiPageResponse<AiProductResponse> getProducts(
            int page,
            int limit,
            String sortBy,
            String sortDir,
            String status,
            Long categoryId,
            String color,
            String size,
            String q) {

        int safePage = Math.max(0, page);
        int safeLimit = Math.min(Math.max(1, limit), MAX_LIMIT);

        StringBuilder jpql = new StringBuilder("SELECT DISTINCT p FROM Product p ");
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(DISTINCT p) FROM Product p ");
        List<String> joins = new ArrayList<>();
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        boolean needVariantJoin = (color != null && !color.isBlank())
                || (size != null && !size.isBlank())
                || (q != null && !q.isBlank());

        if (needVariantJoin) {
            joins.add("LEFT JOIN ProductVariant v ON v.product.id = p.id AND v.isActive = true");
        }
        if (q != null && !q.isBlank()) {
            joins.add("LEFT JOIN p.category c");
        }

        if (status != null && !status.isBlank()) {
            if ("active".equalsIgnoreCase(status)) {
                where.add("p.isActive = true");
            } else if ("inactive".equalsIgnoreCase(status)) {
                where.add("p.isActive = false");
            }
        }

        if (categoryId != null) {
            where.add("p.categoryId = :categoryId");
            params.put("categoryId", categoryId);
        }

        if (color != null && !color.isBlank()) {
            where.add("LOWER(v.color) LIKE LOWER(CONCAT('%', :color, '%'))");
            params.put("color", color.trim());
        }

        if (size != null && !size.isBlank()) {
            where.add("LOWER(v.size) = LOWER(:size)");
            params.put("size", size.trim());
        }

        if (q != null && !q.isBlank()) {
            where.add("(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(v.sku) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(v.color) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(v.size) LIKE LOWER(CONCAT('%', :q, '%')) "
                    + "OR LOWER(v.groupName) LIKE LOWER(CONCAT('%', :q, '%')))");
            params.put("q", q.trim());
        }

        String joinClause = String.join(" ", joins);
        String whereClause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);

        String validSort = switch (sortBy != null ? sortBy : "id") {
            case "name" -> "p.name";
            case "createdAt" -> "p.createdAt";
            case "updatedAt" -> "p.updatedAt";
            default -> "p.id";
        };
        String dir = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql + joinClause + whereClause, Long.class);
        TypedQuery<Product> dataQuery = entityManager.createQuery(
                jpql + joinClause + whereClause + " ORDER BY " + validSort + " " + dir, Product.class);

        for (Map.Entry<String, Object> e : params.entrySet()) {
            countQuery.setParameter(e.getKey(), e.getValue());
            dataQuery.setParameter(e.getKey(), e.getValue());
        }

        long totalItems = countQuery.getSingleResult();
        dataQuery.setFirstResult(safePage * safeLimit);
        dataQuery.setMaxResults(safeLimit);
        List<Product> products = dataQuery.getResultList();

        List<AiProductResponse> items = mapProductsBatch(products);
        int totalPages = (int) Math.ceil((double) totalItems / safeLimit);

        return AiPageResponse.<AiProductResponse>builder()
                .items(items)
                .page(safePage)
                .limit(safeLimit)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .build();
    }

    public AiProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));
        return mapProductsBatch(List.of(product)).get(0);
    }

    public List<AiProductResponse.VariantSummary> getProductVariants(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new NoSuchElementException("Product not found with id: " + productId);
        }
        return variantRepository.findByProductIdAndIsActiveTrue(productId).stream()
                .map(this::toVariantSummary)
                .toList();
    }

    public List<AiProductResponse.ImageSummary> getProductImages(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new NoSuchElementException("Product not found with id: " + productId);
        }
        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
        List<AiProductResponse.ImageSummary> result = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            ProductImage img = images.get(i);
            result.add(AiProductResponse.ImageSummary.builder()
                    .url(normalizeMediaUrl(img.getUrl()))
                    .primary(i == 0)
                    .variantId(img.getVariantId())
                    .build());
        }
        return result;
    }

    public List<Map<String, Object>> getCategories() {
        return categoryRepository.findAll().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("slug", c.getSlug());
            m.put("imageUrl", normalizeMediaUrl(c.getImageUrl()));
            return m;
        }).toList();
    }

    public Map<String, Object> getBrandInfo() {
        Organization org = organizationRepository.findFirstByOrderByIdAsc().orElse(null);
        List<Map<String, Object>> brands = brandRepository.findAll().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("name", b.getName());
            m.put("slug", b.getSlug());
            m.put("logoUrl", normalizeMediaUrl(b.getLogoUrl()));
            m.put("description", b.getDescription());
            return m;
        }).toList();

        Map<String, Object> res = new LinkedHashMap<>();
        if (org != null) {
            Map<String, Object> orgMap = new LinkedHashMap<>();
            orgMap.put("name", org.getName());
            orgMap.put("address", org.getAddress());
            orgMap.put("email", org.getEmail());
            orgMap.put("phoneNumber", org.getPhoneNumber());
            orgMap.put("originLocationName", org.getOriginLocationName());
            orgMap.put("logoUrl", normalizeMediaUrl(org.getLogoUrl()));
            res.put("organization", orgMap);
        } else {
            res.put("organization", null);
        }
        res.put("brands", brands);
        return res;
    }

    public Map<String, Object> getDashboardSummary() {
        long totalProducts = productRepository.count();
        long activeProducts = entityManager.createQuery(
                "SELECT COUNT(p) FROM Product p WHERE p.isActive = true", Long.class).getSingleResult();
        long totalCategories = categoryRepository.count();
        long totalOrders = orderRepository.count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalProducts", totalProducts);
        summary.put("activeProducts", activeProducts);
        summary.put("totalCategories", totalCategories);
        summary.put("totalOrders", totalOrders);
        return summary;
    }

    public Map<String, Object> getSalesSummary() {
        List<Object[]> rows = entityManager.createQuery(
                "SELECT COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN ('PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'COMPLETED')",
                Object[].class).getResultList();
        long paidOrders = 0;
        double totalRevenue = 0;
        if (!rows.isEmpty()) {
            paidOrders = ((Number) rows.get(0)[0]).longValue();
            totalRevenue = ((Number) rows.get(0)[1]).doubleValue();
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("paidOrdersCount", paidOrders);
        res.put("totalRevenue", totalRevenue);
        res.put("currency", "IDR");
        return res;
    }

    public Map<String, Object> getOrdersSummary() {
        List<Object[]> grouped = entityManager.createQuery(
                "SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status", Object[].class).getResultList();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : grouped) {
            String st = row[0] != null ? row[0].toString() : "UNKNOWN";
            long cnt = ((Number) row[1]).longValue();
            byStatus.put(st, cnt);
            total += cnt;
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalOrders", total);
        res.put("ordersByStatus", byStatus);
        return res;
    }

    private List<AiProductResponse> mapProductsBatch(List<Product> products) {
        if (products.isEmpty()) return List.of();

        List<Long> productIds = products.stream().map(Product::getId).toList();

        Map<Long, List<ProductVariant>> variantsByProduct = entityManager.createQuery(
                "SELECT v FROM ProductVariant v WHERE v.product.id IN :ids AND v.isActive = true ORDER BY v.id ASC",
                ProductVariant.class)
                .setParameter("ids", productIds)
                .getResultList()
                .stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        Map<Long, List<ProductImage>> imagesByProduct = entityManager.createQuery(
                "SELECT i FROM ProductImage i WHERE i.product.id IN :ids ORDER BY i.sortOrder ASC, i.id ASC",
                ProductImage.class)
                .setParameter("ids", productIds)
                .getResultList()
                .stream()
                .collect(Collectors.groupingBy(i -> i.getProduct().getId()));

        Set<Long> catIds = products.stream().map(Product::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Category> categoryMap = catIds.isEmpty() ? Map.of() : categoryRepository.findAllById(catIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        Set<Long> brandIds = products.stream().map(Product::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandIds.isEmpty() ? Map.of() : brandRepository.findAllById(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b));

        List<AiProductResponse> result = new ArrayList<>(products.size());
        for (Product p : products) {
            List<ProductVariant> pVariants = variantsByProduct.getOrDefault(p.getId(), List.of());
            List<ProductImage> pImages = imagesByProduct.getOrDefault(p.getId(), List.of());

            Category cat = p.getCategoryId() != null ? categoryMap.get(p.getCategoryId()) : null;
            Brand brand = p.getBrandId() != null ? brandMap.get(p.getBrandId()) : null;

            BigDecimal normal = null;
            BigDecimal selling = null;
            BigDecimal promo = null;
            if (!pVariants.isEmpty()) {
                ProductVariant first = pVariants.get(0);
                normal = first.getPrice();
                if (first.getDiscountPrice() != null && first.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0) {
                    promo = first.getDiscountPrice();
                    selling = first.getDiscountPrice();
                } else {
                    selling = first.getPrice();
                }
            }

            List<AiProductResponse.ImageSummary> imgSummaries = new ArrayList<>();
            for (int i = 0; i < pImages.size(); i++) {
                ProductImage img = pImages.get(i);
                imgSummaries.add(AiProductResponse.ImageSummary.builder()
                        .url(normalizeMediaUrl(img.getUrl()))
                        .primary(i == 0)
                        .variantId(img.getVariantId())
                        .build());
            }

            result.add(AiProductResponse.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .slug(p.getSlug())
                    .sku(p.getSku())
                    .description(p.getDescription())
                    .shortDescription(p.getShortDescription())
                    .category(cat == null ? null : AiProductResponse.CategorySummary.builder()
                            .id(cat.getId()).name(cat.getName()).slug(cat.getSlug()).build())
                    .brand(brand == null ? null : AiProductResponse.BrandSummary.builder()
                            .id(brand.getId()).name(brand.getName()).slug(brand.getSlug()).build())
                    .price(AiProductResponse.PriceSummary.builder()
                            .normal(normal)
                            .selling(selling)
                            .promo(promo)
                            .currency("IDR")
                            .build())
                    .variants(pVariants.stream().map(this::toVariantSummary).toList())
                    .images(imgSummaries)
                    .status(Boolean.TRUE.equals(p.getIsActive()) ? "active" : "inactive")
                    .weight(p.getWeight())
                    .gender(p.getGender())
                    .material(p.getMaterial())
                    .build());
        }
        return result;
    }

    private AiProductResponse.VariantSummary toVariantSummary(ProductVariant v) {
        return AiProductResponse.VariantSummary.builder()
                .id(v.getId())
                .sku(v.getSku())
                .name(v.getGroupName() != null && !v.getGroupName().isBlank() ? v.getGroupName() : v.getColor())
                .size(v.getSize())
                .color(v.getColor())
                .hexColor(v.getHexColor())
                .price(v.getPrice())
                .discountPrice(v.getDiscountPrice())
                .stock(v.getStock())
                .status(Boolean.TRUE.equals(v.getIsActive()) ? "active" : "inactive")
                .imageUrl(normalizeMediaUrl(v.getImageUrl()))
                .build();
    }

    private String normalizeMediaUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return null;
        String base = publicStorageUrl.endsWith("/")
                ? publicStorageUrl.substring(0, publicStorageUrl.length() - 1)
                : publicStorageUrl;
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl
                    .replace("http://zelvira-minio:9000", base)
                    .replace("http://zelixa-minio:9000", base)
                    .replace("http://minio:9000", base)
                    .replace("https://minio.zelvira.my.id", base)
                    .replace("https://minio.zelvira.co.id", base)
                    .replace("https://storage.zelvira.my.id", base)
                    .replace("https://storage.zelvira.co.id", base);
        }
        return base + (rawUrl.startsWith("/") ? rawUrl : "/" + rawUrl);
    }
}
