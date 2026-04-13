package com.example.demo.repository;

import com.example.demo.entity.BannerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<BannerEntity, Long> {
    List<BannerEntity> findByActiveTrueOrderByDisplayOrderAsc();
    List<BannerEntity> findAllByOrderByDisplayOrderAsc();
}
