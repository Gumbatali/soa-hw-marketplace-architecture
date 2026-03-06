package com.gumbatali.marketplace.web;

import com.gumbatali.marketplace.generated.api.ProductsApi;
import com.gumbatali.marketplace.generated.model.ProductCreate;
import com.gumbatali.marketplace.generated.model.ProductPageResponse;
import com.gumbatali.marketplace.generated.model.ProductResponse;
import com.gumbatali.marketplace.generated.model.ProductStatus;
import com.gumbatali.marketplace.generated.model.ProductUpdate;
import com.gumbatali.marketplace.security.SecurityUtils;
import com.gumbatali.marketplace.service.ProductService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductsController implements ProductsApi {

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    @Override
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(ProductCreate productCreate) {
        // Дополнительно к аннотации role-check в сервисе есть проверка ownership.
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(productService.createProduct(productCreate, SecurityUtils.currentUser()));
    }

    @Override
    @PreAuthorize("hasAnyRole('USER','SELLER','ADMIN')")
    public ResponseEntity<ProductPageResponse> listProducts(Integer page, Integer size, ProductStatus status, String category) {
        return ResponseEntity.ok(productService.listProducts(page, size, status, category));
    }

    @Override
    @PreAuthorize("hasAnyRole('USER','SELLER','ADMIN')")
    public ResponseEntity<ProductResponse> getProductById(UUID id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @Override
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(UUID id, ProductUpdate productUpdate) {
        return ResponseEntity.ok(productService.updateProduct(id, productUpdate, SecurityUtils.currentUser()));
    }

    @Override
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<Void> deleteProduct(UUID id) {
        // DELETE здесь мягкий: внутри ставится статус ARCHIVED.
        productService.deleteProduct(id, SecurityUtils.currentUser());
        return ResponseEntity.noContent().build();
    }
}
