package com.gumbatali.marketplace.service;

import com.gumbatali.marketplace.common.error.ApiException;
import com.gumbatali.marketplace.common.error.ErrorCode;
import com.gumbatali.marketplace.config.AppProperties;
import com.gumbatali.marketplace.domain.model.OrderEntity;
import com.gumbatali.marketplace.domain.model.OrderItemEntity;
import com.gumbatali.marketplace.domain.model.OrderStatus;
import com.gumbatali.marketplace.domain.model.ProductEntity;
import com.gumbatali.marketplace.domain.model.ProductStatus;
import com.gumbatali.marketplace.domain.model.PromoCodeEntity;
import com.gumbatali.marketplace.domain.model.UserOperationEntity;
import com.gumbatali.marketplace.domain.model.UserOperationType;
import com.gumbatali.marketplace.domain.model.UserRole;
import com.gumbatali.marketplace.domain.repository.OrderRepository;
import com.gumbatali.marketplace.domain.repository.ProductRepository;
import com.gumbatali.marketplace.domain.repository.PromoCodeRepository;
import com.gumbatali.marketplace.domain.repository.UserOperationRepository;
import com.gumbatali.marketplace.generated.model.OrderCreateRequest;
import com.gumbatali.marketplace.generated.model.OrderItemRequest;
import com.gumbatali.marketplace.generated.model.OrderPageResponse;
import com.gumbatali.marketplace.generated.model.OrderResponse;
import com.gumbatali.marketplace.generated.model.OrderStatusUpdateRequest;
import com.gumbatali.marketplace.generated.model.OrderUpdateRequest;
import com.gumbatali.marketplace.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_PERCENTAGE_DISCOUNT = BigDecimal.valueOf(70);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final UserOperationRepository userOperationRepository;
    private final AppProperties appProperties;
    private final ApiMapper apiMapper;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        PromoCodeRepository promoCodeRepository,
                        UserOperationRepository userOperationRepository,
                        AppProperties appProperties,
                        ApiMapper apiMapper) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.userOperationRepository = userOperationRepository;
        this.appProperties = appProperties;
        this.apiMapper = apiMapper;
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request, AuthenticatedUser user) {
        if (user.role() == UserRole.SELLER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        validateRateLimit(user.id(), UserOperationType.CREATE_ORDER, now);

        boolean hasActiveOrder = orderRepository.existsByUserIdAndStatusIn(
            user.id(),
            List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING)
        );
        if (hasActiveOrder) {
            throw new ApiException(ErrorCode.ORDER_HAS_ACTIVE);
        }

        Map<UUID, Integer> requested = aggregateQuantities(request.getItems());
        Map<UUID, ProductEntity> products = loadAndLockProducts(requested.keySet());
        validateProductsAreActive(products, requested.keySet());
        validateStock(products, requested);
        reserveStock(products, requested);

        List<OrderItemEntity> orderItems = buildOrderItems(requested, products);
        BigDecimal subtotal = calculateSubtotal(orderItems);

        OrderEntity order = new OrderEntity();
        order.setUserId(user.id());
        order.setStatus(OrderStatus.CREATED);

        Pricing pricing = applyPromoOnCreate(request.getPromoCode(), subtotal, now);
        order.setPromoCode(pricing.promoCode);
        order.setDiscountAmount(pricing.discount);
        order.setTotalAmount(pricing.total);
        order.replaceItems(orderItems);

        OrderEntity saved = orderRepository.save(order);
        recordOperation(user.id(), UserOperationType.CREATE_ORDER);
        return apiMapper.toOrderResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, AuthenticatedUser user) {
        OrderEntity order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        verifyOrderOwnership(user, order);
        return apiMapper.toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderPageResponse listOrders(Integer page,
                                        Integer size,
                                        com.gumbatali.marketplace.generated.model.OrderStatus status,
                                        AuthenticatedUser user) {
        if (user.role() == UserRole.SELLER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        Specification<OrderEntity> spec = Specification.where(null);
        if (user.role() == UserRole.USER) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), user.id()));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), apiMapper.toDomainOrderStatus(status)));
        }

        Page<OrderEntity> result = orderRepository.findAll(
            spec,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        OrderPageResponse response = new OrderPageResponse();
        response.setContent(result.getContent().stream().map(apiMapper::toOrderResponse).toList());
        response.setTotalElements(result.getTotalElements());
        response.setPage(page);
        response.setSize(size);
        return response;
    }

    @Transactional
    public OrderResponse updateOrder(UUID orderId, OrderUpdateRequest request, AuthenticatedUser user) {
        if (user.role() == UserRole.SELLER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));

        verifyOrderOwnership(user, order);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        validateRateLimit(user.id(), UserOperationType.UPDATE_ORDER, now);

        Map<UUID, Integer> oldQuantities = aggregateFromExistingItems(order.getItems());
        Map<UUID, Integer> newQuantities = aggregateQuantities(request.getItems());

        List<UUID> allProductIds = new ArrayList<>(oldQuantities.keySet());
        for (UUID id : newQuantities.keySet()) {
            if (!allProductIds.contains(id)) {
                allProductIds.add(id);
            }
        }

        Map<UUID, ProductEntity> products = loadAndLockProducts(allProductIds);

        // Return previously reserved stock first.
        for (Map.Entry<UUID, Integer> oldItem : oldQuantities.entrySet()) {
            ProductEntity product = products.get(oldItem.getKey());
            if (product != null) {
                product.setStock(product.getStock() + oldItem.getValue());
            }
        }

        validateProductsAreActive(products, newQuantities.keySet());
        validateStock(products, newQuantities);
        reserveStock(products, newQuantities);

        List<OrderItemEntity> newItems = buildOrderItems(newQuantities, products);
        BigDecimal subtotal = calculateSubtotal(newItems);

        Pricing pricing = applyPromoOnUpdate(order, subtotal, now);
        order.replaceItems(newItems);
        order.setDiscountAmount(pricing.discount);
        order.setTotalAmount(pricing.total);
        order.setPromoCode(pricing.promoCode);

        OrderEntity saved = orderRepository.save(order);
        recordOperation(user.id(), UserOperationType.UPDATE_ORDER);
        return apiMapper.toOrderResponse(saved);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, AuthenticatedUser user) {
        if (user.role() == UserRole.SELLER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));

        verifyOrderOwnership(user, order);
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        Map<UUID, Integer> orderQuantities = aggregateFromExistingItems(order.getItems());
        Map<UUID, ProductEntity> products = loadAndLockProducts(orderQuantities.keySet());

        for (Map.Entry<UUID, Integer> entry : orderQuantities.entrySet()) {
            ProductEntity product = products.get(entry.getKey());
            if (product != null) {
                product.setStock(product.getStock() + entry.getValue());
            }
        }

        if (order.getPromoCode() != null) {
            PromoCodeEntity promoCode = promoCodeRepository.findByIdForUpdate(order.getPromoCode().getId())
                .orElse(order.getPromoCode());
            if (promoCode.getCurrentUses() > 0) {
                promoCode.setCurrentUses(promoCode.getCurrentUses() - 1);
            }
            order.setPromoCode(promoCode);
        }

        order.setStatus(OrderStatus.CANCELED);
        OrderEntity saved = orderRepository.save(order);
        return apiMapper.toOrderResponse(saved);
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId,
                                           OrderStatusUpdateRequest request,
                                           AuthenticatedUser user) {
        if (user.role() == UserRole.SELLER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));

        verifyOrderOwnership(user, order);
        OrderStatus nextStatus = apiMapper.toDomainOrderStatus(request.getNextStatus());
        validateStateTransition(order.getStatus(), nextStatus);

        order.setStatus(nextStatus);
        OrderEntity saved = orderRepository.save(order);
        return apiMapper.toOrderResponse(saved);
    }

    private Map<UUID, ProductEntity> loadAndLockProducts(Collection<UUID> productIds) {
        Map<UUID, ProductEntity> map = new HashMap<>();
        if (productIds.isEmpty()) {
            return map;
        }

        List<ProductEntity> products = productRepository.lockAllByIdIn(productIds);
        for (ProductEntity product : products) {
            map.put(product.getId(), product);
        }
        return map;
    }

    private void validateProductsAreActive(Map<UUID, ProductEntity> products, Collection<UUID> expectedIds) {
        for (UUID productId : expectedIds) {
            ProductEntity product = products.get(productId);
            if (product == null) {
                throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ApiException(ErrorCode.PRODUCT_INACTIVE);
            }
        }
    }

    private void validateStock(Map<UUID, ProductEntity> products, Map<UUID, Integer> requested) {
        List<Map<String, Object>> insufficient = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : requested.entrySet()) {
            ProductEntity product = products.get(entry.getKey());
            if (product == null) {
                continue;
            }
            if (product.getStock() < entry.getValue()) {
                insufficient.add(Map.of(
                    "product_id", product.getId(),
                    "requested", entry.getValue(),
                    "available", product.getStock()
                ));
            }
        }

        if (!insufficient.isEmpty()) {
            throw new ApiException(
                ErrorCode.INSUFFICIENT_STOCK,
                ErrorCode.INSUFFICIENT_STOCK.defaultMessage(),
                Map.of("items", insufficient)
            );
        }
    }

    private void reserveStock(Map<UUID, ProductEntity> products, Map<UUID, Integer> requested) {
        for (Map.Entry<UUID, Integer> entry : requested.entrySet()) {
            ProductEntity product = products.get(entry.getKey());
            if (product != null) {
                product.setStock(product.getStock() - entry.getValue());
            }
        }
    }

    private List<OrderItemEntity> buildOrderItems(Map<UUID, Integer> requested, Map<UUID, ProductEntity> products) {
        List<OrderItemEntity> items = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : requested.entrySet()) {
            ProductEntity product = products.get(entry.getKey());
            OrderItemEntity item = new OrderItemEntity();
            item.setProductId(entry.getKey());
            item.setQuantity(entry.getValue());
            item.setPriceAtOrder(product.getPrice());
            items.add(item);
        }
        return items;
    }

    private BigDecimal calculateSubtotal(List<OrderItemEntity> items) {
        return items.stream()
            .map(item -> item.getPriceAtOrder().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private Pricing applyPromoOnCreate(String promoCode,
                                       BigDecimal subtotal,
                                       OffsetDateTime now) {
        if (promoCode == null || promoCode.isBlank()) {
            return new Pricing(BigDecimal.ZERO, subtotal, null);
        }

        PromoCodeEntity entity = promoCodeRepository.findByCodeForUpdate(promoCode)
            .orElseThrow(() -> new ApiException(ErrorCode.PROMO_CODE_INVALID));

        if (!isPromoValidForCreate(entity, now)) {
            throw new ApiException(ErrorCode.PROMO_CODE_INVALID);
        }

        if (subtotal.compareTo(entity.getMinOrderAmount()) < 0) {
            throw new ApiException(ErrorCode.PROMO_CODE_MIN_AMOUNT);
        }

        BigDecimal discount = calculateDiscount(subtotal, entity);
        entity.setCurrentUses(entity.getCurrentUses() + 1);
        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        return new Pricing(discount, total, entity);
    }

    private Pricing applyPromoOnUpdate(OrderEntity order,
                                       BigDecimal subtotal,
                                       OffsetDateTime now) {
        PromoCodeEntity promoCode = order.getPromoCode();
        if (promoCode == null) {
            return new Pricing(BigDecimal.ZERO, subtotal, null);
        }

        PromoCodeEntity lockedPromo = promoCodeRepository.findByIdForUpdate(promoCode.getId())
            .orElseThrow(() -> new ApiException(ErrorCode.PROMO_CODE_INVALID));

        if (!isPromoValidForUpdate(lockedPromo, now)) {
            throw new ApiException(ErrorCode.PROMO_CODE_INVALID);
        }

        if (subtotal.compareTo(lockedPromo.getMinOrderAmount()) < 0) {
            if (lockedPromo.getCurrentUses() > 0) {
                lockedPromo.setCurrentUses(lockedPromo.getCurrentUses() - 1);
            }
            return new Pricing(BigDecimal.ZERO, subtotal, null);
        }

        BigDecimal discount = calculateDiscount(subtotal, lockedPromo);
        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        return new Pricing(discount, total, lockedPromo);
    }

    private BigDecimal calculateDiscount(BigDecimal subtotal, PromoCodeEntity promoCode) {
        BigDecimal discount;
        if (promoCode.getDiscountType() == com.gumbatali.marketplace.domain.model.DiscountType.PERCENTAGE) {
            BigDecimal raw = subtotal.multiply(promoCode.getDiscountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal maxAllowed = subtotal.multiply(MAX_PERCENTAGE_DISCOUNT).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            discount = raw.min(maxAllowed);
        } else {
            discount = promoCode.getDiscountValue().min(subtotal);
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateRateLimit(UUID userId, UserOperationType operationType, OffsetDateTime now) {
        userOperationRepository.findTopByUserIdAndOperationTypeOrderByCreatedAtDesc(userId, operationType)
            .ifPresent(lastOp -> {
                OffsetDateTime allowedAt = lastOp.getCreatedAt().plusMinutes(appProperties.order().rateLimitMinutes());
                if (now.isBefore(allowedAt)) {
                    throw new ApiException(
                        ErrorCode.ORDER_LIMIT_EXCEEDED,
                        ErrorCode.ORDER_LIMIT_EXCEEDED.defaultMessage(),
                        Map.of("next_allowed_at", allowedAt)
                    );
                }
            });
    }

    private void recordOperation(UUID userId, UserOperationType operationType) {
        UserOperationEntity operation = new UserOperationEntity();
        operation.setUserId(userId);
        operation.setOperationType(operationType);
        userOperationRepository.save(operation);
    }

    private void verifyOrderOwnership(AuthenticatedUser user, OrderEntity order) {
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (!order.getUserId().equals(user.id())) {
            throw new ApiException(ErrorCode.ORDER_OWNERSHIP_VIOLATION);
        }
    }

    private boolean isPromoValidForCreate(PromoCodeEntity promoCode, OffsetDateTime now) {
        return promoCode.getActive()
            && promoCode.getCurrentUses() < promoCode.getMaxUses()
            && !now.isBefore(promoCode.getValidFrom())
            && !now.isAfter(promoCode.getValidUntil());
    }

    private boolean isPromoValidForUpdate(PromoCodeEntity promoCode, OffsetDateTime now) {
        return promoCode.getActive()
            && promoCode.getCurrentUses() <= promoCode.getMaxUses()
            && !now.isBefore(promoCode.getValidFrom())
            && !now.isAfter(promoCode.getValidUntil());
    }

    private Map<UUID, Integer> aggregateQuantities(List<OrderItemRequest> items) {
        Map<UUID, Integer> aggregated = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            aggregated.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        return aggregated;
    }

    private Map<UUID, Integer> aggregateFromExistingItems(List<OrderItemEntity> items) {
        Map<UUID, Integer> aggregated = new LinkedHashMap<>();
        for (OrderItemEntity item : items) {
            aggregated.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        return aggregated;
    }

    private void validateStateTransition(OrderStatus from, OrderStatus to) {
        boolean allowed = switch (from) {
            case CREATED -> to == OrderStatus.PAYMENT_PENDING;
            case PAYMENT_PENDING -> to == OrderStatus.PAID;
            case PAID -> to == OrderStatus.SHIPPED;
            case SHIPPED -> to == OrderStatus.COMPLETED;
            default -> false;
        };

        if (!allowed) {
            throw new ApiException(
                ErrorCode.INVALID_STATE_TRANSITION,
                ErrorCode.INVALID_STATE_TRANSITION.defaultMessage(),
                Map.of("from", from.name(), "to", to.name())
            );
        }
    }

    private record Pricing(BigDecimal discount, BigDecimal total, PromoCodeEntity promoCode) {
    }
}
