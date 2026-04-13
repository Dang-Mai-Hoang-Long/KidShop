package com.example.demo.repository;

import com.example.demo.entity.ShippingAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddressEntity, Long> {
    List<ShippingAddressEntity> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(Long userId);
    Optional<ShippingAddressEntity> findByIdAndUserId(Long id, Long userId);
    Optional<ShippingAddressEntity> findFirstByUserIdAndDefaultAddressTrue(Long userId);
    long countByUserId(Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
}