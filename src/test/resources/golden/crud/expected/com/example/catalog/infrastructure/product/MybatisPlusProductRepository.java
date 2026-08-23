package com.example.catalog.infrastructure.product;

import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.catalog.domain.product.Product;
import com.example.catalog.domain.product.ProductRepository;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-Plus repository adapter for Product.
 */
@Repository
public class MybatisPlusProductRepository implements ProductRepository {

    private final ProductMapper mapper;

    public MybatisPlusProductRepository(ProductMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(this.mapper.selectById(id))
                .map(ProductEntity::toDomain);
    }

    @Override
    public PageResult findPage(long page, long size) {
        IPage<ProductEntity> result = this.mapper.selectPage(Page.of(page, size), null);
        return new PageResult(
                result.getRecords().stream().map(ProductEntity::toDomain).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    @Override
    public Product create(Product product) {
        ProductEntity entity = ProductEntity.fromDomain(product);
        this.mapper.insert(entity);
        return entity.toDomain();
    }

    @Override
    public Product update(Product product) {
        ProductEntity entity = ProductEntity.fromDomain(product);
        this.mapper.updateById(entity);
        return entity.toDomain();
    }

    @Override
    public void deleteById(Long id) {
        this.mapper.deleteById(id);
    }
}
