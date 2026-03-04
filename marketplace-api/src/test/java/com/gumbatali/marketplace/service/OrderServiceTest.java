package com.gumbatali.marketplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gumbatali.marketplace.common.error.ApiException;
import com.gumbatali.marketplace.common.error.ErrorCode;
import com.gumbatali.marketplace.config.AppProperties;
import com.gumbatali.marketplace.domain.model.ProductEntity;
import com.gumbatali.marketplace.domain.model.ProductStatus;
import com.gumbatali.marketplace.domain.model.UserRole;
import com.gumbatali.marketplace.domain.repository.OrderRepository;
import com.gumbatali.marketplace.domain.repository.ProductRepository;
import com.gumbatali.marketplace.domain.repository.PromoCodeRepository;
import com.gumbatali.marketplace.domain.repository.UserOperationRepository;
import com.gumbatali.marketplace.generated.model.OrderCreateRequest;
import com.gumbatali.marketplace.generated.model.OrderItemRequest;
import com.gumbatali.marketplace.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @Mock
    private UserOperationRepository userOperationRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(
            new AppProperties.Auth(20, 14, "01234567890123456789012345678901"),
            new AppProperties.Order(5)
        );
        orderService = new OrderService(
            orderRepository,
            productRepository,
            promoCodeRepository,
            userOperationRepository,
            appProperties,
            new ApiMapper()
        );
    }

    @Test
    void createOrderShouldFailWhenProductInactive() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(userOperationRepository.findTopByUserIdAndOperationTypeOrderByCreatedAtDesc(any(), any()))
            .thenReturn(Optional.empty());
        when(orderRepository.existsByUserIdAndStatusIn(any(), any())).thenReturn(false);

        ProductEntity product = new ProductEntity();
        product.setId(productId);
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(10);
        product.setPrice(BigDecimal.TEN);

        when(productRepository.lockAllByIdIn(any())).thenReturn(List.of(product));

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);

        OrderCreateRequest request = new OrderCreateRequest();
        request.setItems(List.of(item));

        AuthenticatedUser user = new AuthenticatedUser(userId, "user", UserRole.USER);

        ApiException exception = assertThrows(ApiException.class, () -> orderService.createOrder(request, user));
        assertEquals(ErrorCode.PRODUCT_INACTIVE, exception.getErrorCode());
    }
}
