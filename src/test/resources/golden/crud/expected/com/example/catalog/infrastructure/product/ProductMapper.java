package com.example.catalog.infrastructure.product;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for Product.
 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {
}
