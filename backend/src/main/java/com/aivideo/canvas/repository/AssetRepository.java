package com.aivideo.canvas.repository;

import com.aivideo.canvas.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findByIdAndUserId(Long id, Long userId);
}
