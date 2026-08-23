package com.example.catalog.infrastructure.product;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.catalog.domain.product.Product;

/**
 * MyBatis-Plus persistence entity for Product.
 */
@TableName("catalog_product")
public class ProductEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("sku")
    private String sku;

    @TableField("name")
    private String name;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return this.sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getUnitPrice() {
        return this.unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    Product toDomain() {
        return new Product(
                this.id,
                this.sku,
                this.name,
                this.unitPrice
        );
    }

    static ProductEntity fromDomain(Product source) {
        ProductEntity entity = new ProductEntity();
        entity.setId(source.id());
        entity.setSku(source.sku());
        entity.setName(source.name());
        entity.setUnitPrice(source.unitPrice());
        return entity;
    }
}
