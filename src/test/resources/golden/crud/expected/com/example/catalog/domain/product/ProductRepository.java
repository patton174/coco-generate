package com.example.catalog.domain.product;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for Product.
 */
public interface ProductRepository {

    Optional<Product> findById(Long id);

    PageResult findPage(long page, long size);

    Product create(Product product);

    Product update(Product product);

    void deleteById(Long id);

    record PageResult(List<Product> items, long total, long page, long size) {

        public PageResult {
            items = List.copyOf(items);
        }
    }
}
