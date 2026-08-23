package com.example.catalog.interfaces.rest.product;


import com.example.catalog.application.product.ProductApplicationService;
import com.example.catalog.domain.product.Product;
import com.example.catalog.interfaces.rest.product.dto.CreateProductRequest;
import com.example.catalog.interfaces.rest.product.dto.ProductResponse;
import com.example.catalog.interfaces.rest.product.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for Product.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductApplicationService applicationService;

    public ProductController(ProductApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable("id") Long id) {
        return ProductResponse.from(this.applicationService.get(id));
    }

    @GetMapping
    public ProductResponse.Page list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ProductResponse.Page.from(this.applicationService.list(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        Product created = this.applicationService.create(new Product(
                null,
                request.sku(),
                request.name(),
                request.unitPrice()
        ));
        return ProductResponse.from(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        Product updated = this.applicationService.update(new Product(
                id,
                request.sku(),
                request.name(),
                request.unitPrice()
        ));
        return ProductResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        this.applicationService.delete(id);
    }
}
