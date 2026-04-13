package com.example.demo.repository;

import com.example.demo.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {
    List<CartItemEntity> findByUserIdOrderByIdDesc(Long userId);
    Optional<CartItemEntity> findByUserIdAndProductIdAndSelectedSize(Long userId, Long productId, String selectedSize);
    void deleteByUserId(Long userId);
    long countByUserId(Long userId);
}
