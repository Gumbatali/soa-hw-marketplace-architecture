package com.gumbatali.marketplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gumbatali.marketplace.common.error.ApiException;
import com.gumbatali.marketplace.common.error.ErrorCode;
import com.gumbatali.marketplace.domain.model.UserRole;
import com.gumbatali.marketplace.domain.repository.ProductRepository;
import com.gumbatali.marketplace.generated.model.ProductCreate;
import com.gumbatali.marketplace.generated.model.ProductStatus;
import com.gumbatali.marketplace.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, new ApiMapper());
    }

    @Test
    void createProductShouldRejectUserRole() {
        // GIVEN: обычный USER пытается создать товар
        ProductCreate request = new ProductCreate();
        request.setName("name");
        request.setDescription("desc");
        request.setPrice(BigDecimal.TEN);
        request.setStock(1);
        request.setCategory("cat");
        request.setStatus(ProductStatus.ACTIVE);

        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "john", UserRole.USER);

        // THEN: доступ должен быть запрещен
        ApiException exception = assertThrows(ApiException.class, () -> productService.createProduct(request, user));
        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }
}
