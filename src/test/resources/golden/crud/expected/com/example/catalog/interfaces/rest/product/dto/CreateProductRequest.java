package com.example.catalog.interfaces.rest.product.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for creating Product.
 */
public record CreateProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        @NotNull BigDecimal unitPrice
) {
}
