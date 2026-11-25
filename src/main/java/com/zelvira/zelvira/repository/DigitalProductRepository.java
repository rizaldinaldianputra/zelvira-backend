package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.DigitalProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DigitalProductRepository extends JpaRepository<DigitalProduct, Long> {
        Optional<DigitalProduct> findByBuyerSkuCode(String buyerSkuCode);

        List<DigitalProduct> findByCategoryContainingIgnoreCase(String category);

        List<DigitalProduct> findByCategoryContainingIgnoreCaseAndIsPostpaid(String category, Boolean isPostpaid);

        List<DigitalProduct> findByIsPostpaid(Boolean isPostpaid);

        List<DigitalProduct> findByIsActive(Boolean isActive);

        List<DigitalProduct> findByCategoryContainingIgnoreCaseAndIsActive(String category, Boolean isActive);

        List<DigitalProduct> findByCategoryContainingIgnoreCaseAndIsPostpaidAndIsActive(String category,
                        Boolean isPostpaid, Boolean isActive);

        List<DigitalProduct> findByIsPostpaidAndIsActive(Boolean isPostpaid, Boolean isActive);

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.category FROM DigitalProduct p WHERE p.category IS NOT NULL AND p.category <> '' ORDER BY p.category ASC")
        List<String> findDistinctCategories();

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.category FROM DigitalProduct p WHERE p.category IS NOT NULL AND p.category <> '' AND p.isPostpaid = :isPostpaid ORDER BY p.category ASC")
        List<String> findDistinctCategoriesByIsPostpaid(
                        @org.springframework.data.repository.query.Param("isPostpaid") Boolean isPostpaid);

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.category FROM DigitalProduct p WHERE p.category IS NOT NULL AND p.category <> '' AND p.isActive = true ORDER BY p.category ASC")
        List<String> findDistinctActiveCategories();

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.category FROM DigitalProduct p WHERE p.category IS NOT NULL AND p.category <> '' AND p.isPostpaid = :isPostpaid AND p.isActive = true ORDER BY p.category ASC")
        List<String> findDistinctActiveCategoriesByIsPostpaid(
                        @org.springframework.data.repository.query.Param("isPostpaid") Boolean isPostpaid);

        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.data.jpa.repository.Query("UPDATE DigitalProduct p SET p.isActive = :isActive WHERE LOWER(p.category) LIKE LOWER(CONCAT('%', :category, '%')) AND p.isPostpaid = :isPostpaid")
        int updateActiveStatusByCategoryAndIsPostpaid(
                        @org.springframework.data.repository.query.Param("category") String category,
                        @org.springframework.data.repository.query.Param("isPostpaid") Boolean isPostpaid,
                        @org.springframework.data.repository.query.Param("isActive") Boolean isActive);

        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.data.jpa.repository.Query("DELETE FROM DigitalProduct p WHERE p.isPostpaid = :isPostpaid")
        void deleteByIsPostpaid(@org.springframework.data.repository.query.Param("isPostpaid") Boolean isPostpaid);

        long countByIsActiveTrue();

        boolean existsByCategoryContainingIgnoreCaseAndIsPostpaidAndIsActive(String category, Boolean isPostpaid, Boolean isActive);
}
