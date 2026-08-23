package com.example.catalog.interfaces.rest.product.dto;

import java.util.List;
import java.math.BigDecimal;

import com.example.catalog.domain.product.Product;
import com.example.catalog.domain.product.ProductRepository;

/**
 * Response body for Product.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        BigDecimal unitPrice
) {

    public static ProductResponse from(Product source) {
        return new ProductResponse(
                source.id(),
                source.sku(),
                source.name(),
                source.unitPrice()
        );
    }

    public record Page(List<ProductResponse> items, long total, long page, long size) {

        public Page {
            items = List.copyOf(items);
        }

        public static Page from(ProductRepository.PageResult source) {
            return new Page(
                    source.items().stream().map(ProductResponse::from).toList(),
                    source.total(),
                    source.page(),
                    source.size());
        }
    }
}
