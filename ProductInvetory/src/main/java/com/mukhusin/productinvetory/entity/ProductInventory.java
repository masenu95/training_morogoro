package com.mukhusin.productinvetory.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "product_inventory")
public class ProductInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long productId;
    private Integer openingQuantity;
    private Integer quantity;
    private Integer closingQuantity;
    @Enumerated(EnumType.STRING)
    private Action action;
    private LocalDate actionDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getOpeningQuantity() {
        return openingQuantity;
    }

    public void setOpeningQuantity(Integer openingQuantity) {
        this.openingQuantity = openingQuantity;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getClosingQuantity() {
        return closingQuantity;
    }

    public void setClosingQuantity(Integer closingQuantity) {
        this.closingQuantity = closingQuantity;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public LocalDate getActionDate() {
        return actionDate;
    }

    public void setActionDate(LocalDate actionDate) {
        this.actionDate = actionDate;
    }
}
