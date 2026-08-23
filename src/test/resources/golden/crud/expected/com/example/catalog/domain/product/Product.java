package com.example.catalog.domain.product;

import java.math.BigDecimal;
/**
 * Domain model for Product.
 */
public record Product(
        Long id,
        String sku,
        String name,
        BigDecimal unitPrice
) {
}
