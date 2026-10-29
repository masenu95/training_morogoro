package com.training.productinvetory.repository;

import com.training.productinvetory.entity.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {
    Optional<ProductInventory> findTopByProductIdOrderByIdDesc(Long productId);
    List<ProductInventory> findByProductId(Long productId);
    List<ProductInventory> findByProductIdAndActionDate(Long productId, LocalDate actionDate);
}
