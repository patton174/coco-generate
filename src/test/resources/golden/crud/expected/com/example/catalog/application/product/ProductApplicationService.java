package com.example.catalog.application.product;


import io.github.coco.exception.CocoCommonErrorCode;
import com.example.catalog.domain.product.Product;
import com.example.catalog.domain.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for Product use cases.
 */
@Service
public class ProductApplicationService {

    private final ProductRepository repository;

    public ProductApplicationService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return this.repository.findById(id)
                .orElseThrow(() -> CocoCommonErrorCode.NOT_FOUND.notFound(
                        "Product:" + id));
    }

    @Transactional(readOnly = true)
    public ProductRepository.PageResult list(long page, long size) {
        if (page < 1 || size < 1 || size > 100) {
            throw CocoCommonErrorCode.INVALID_ARGUMENT.request("page/size");
        }
        return this.repository.findPage(page, size);
    }

    @Transactional
    public Product create(Product product) {
        return this.repository.create(product);
    }

    @Transactional
    public Product update(Product product) {
        get(product.id());
        return this.repository.update(product);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        this.repository.deleteById(id);
    }
}
