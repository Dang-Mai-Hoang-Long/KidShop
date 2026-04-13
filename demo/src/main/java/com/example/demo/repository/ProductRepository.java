package com.example.demo.repository;

import com.example.demo.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

    Page<ProductEntity> findByActiveTrueOrderBySoldCountDesc(Pageable pageable);

    List<ProductEntity> findTop8ByActiveTrueOrderBySoldCountDesc();

    List<ProductEntity> findTop8ByActiveTrueAndFeaturedTrueOrderByCreatedAtDesc();

    List<ProductEntity> findByIsFlashSaleTrueAndActiveTrue();

    List<ProductEntity> findByIsFlashSaleTrue();

    Page<ProductEntity> findByActiveTrueAndCategoryIdOrderByCreatedAtDesc(Long categoryId, Pageable pageable);

    Page<ProductEntity> findByActiveTrueAndCategoryNameIgnoreCaseOrderByCreatedAtDesc(String categoryName, Pageable pageable);

    List<ProductEntity> findByCategoryIdOrderByIdAsc(Long categoryId);

    boolean existsByCategoryId(Long categoryId);

    @Query("SELECT p FROM ProductEntity p WHERE p.active = true AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ProductEntity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    long countByActiveTrue();
}
