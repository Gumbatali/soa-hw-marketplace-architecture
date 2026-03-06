package com.gumbatali.marketplace.service;

import com.gumbatali.marketplace.domain.model.DiscountType;
import com.gumbatali.marketplace.domain.model.OrderEntity;
import com.gumbatali.marketplace.domain.model.OrderItemEntity;
import com.gumbatali.marketplace.domain.model.OrderStatus;
import com.gumbatali.marketplace.domain.model.ProductEntity;
import com.gumbatali.marketplace.domain.model.ProductStatus;
import com.gumbatali.marketplace.domain.model.PromoCodeEntity;
import com.gumbatali.marketplace.domain.model.UserEntity;
import com.gumbatali.marketplace.domain.model.UserRole;
import com.gumbatali.marketplace.generated.model.OrderItemResponse;
import com.gumbatali.marketplace.generated.model.OrderResponse;
import com.gumbatali.marketplace.generated.model.ProductResponse;
import com.gumbatali.marketplace.generated.model.PromoCodeResponse;
import com.gumbatali.marketplace.generated.model.UserResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApiMapper {

    public ProductResponse toProductResponse(ProductEntity entity) {
        ProductResponse response = new ProductResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setPrice(entity.getPrice());
        response.setStock(entity.getStock());
        response.setCategory(entity.getCategory());
        response.setStatus(toApiProductStatus(entity.getStatus()));
        response.setSellerId(entity.getSellerId());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public PromoCodeResponse toPromoCodeResponse(PromoCodeEntity entity) {
        PromoCodeResponse response = new PromoCodeResponse();
        response.setId(entity.getId());
        response.setCode(entity.getCode());
        response.setDiscountType(toApiDiscountType(entity.getDiscountType()));
        response.setDiscountValue(entity.getDiscountValue());
        response.setMinOrderAmount(entity.getMinOrderAmount());
        response.setMaxUses(entity.getMaxUses());
        response.setCurrentUses(entity.getCurrentUses());
        response.setValidFrom(entity.getValidFrom());
        response.setValidUntil(entity.getValidUntil());
        response.setActive(entity.getActive());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public UserResponse toUserResponse(UserEntity user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(toApiRole(user.getRole()));
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }

    public OrderResponse toOrderResponse(OrderEntity order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setStatus(toApiOrderStatus(order.getStatus()));
        response.setPromoCode(order.getPromoCode() != null ? order.getPromoCode().getCode() : null);
        response.setItems(mapOrderItems(order.getItems()));
        response.setTotalAmount(order.getTotalAmount());
        response.setDiscountAmount(order.getDiscountAmount());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }

    public ProductStatus toDomainProductStatus(com.gumbatali.marketplace.generated.model.ProductStatus status) {
        return ProductStatus.valueOf(status.name());
    }

    public com.gumbatali.marketplace.generated.model.ProductStatus toApiProductStatus(ProductStatus status) {
        return com.gumbatali.marketplace.generated.model.ProductStatus.valueOf(status.name());
    }

    public UserRole toDomainRole(com.gumbatali.marketplace.generated.model.Role role) {
        return UserRole.valueOf(role.name());
    }

    public com.gumbatali.marketplace.generated.model.Role toApiRole(UserRole role) {
        return com.gumbatali.marketplace.generated.model.Role.valueOf(role.name());
    }

    public DiscountType toDomainDiscountType(com.gumbatali.marketplace.generated.model.DiscountType discountType) {
        return DiscountType.valueOf(discountType.name());
    }

    public com.gumbatali.marketplace.generated.model.DiscountType toApiDiscountType(DiscountType discountType) {
        return com.gumbatali.marketplace.generated.model.DiscountType.valueOf(discountType.name());
    }

    public com.gumbatali.marketplace.generated.model.OrderStatus toApiOrderStatus(OrderStatus orderStatus) {
        return com.gumbatali.marketplace.generated.model.OrderStatus.valueOf(orderStatus.name());
    }

    public OrderStatus toDomainOrderStatus(com.gumbatali.marketplace.generated.model.OrderStatus orderStatus) {
        return OrderStatus.valueOf(orderStatus.name());
    }

    private List<OrderItemResponse> mapOrderItems(List<OrderItemEntity> items) {
        return items.stream().map(item -> {
            OrderItemResponse response = new OrderItemResponse();
            response.setProductId(item.getProductId());
            response.setQuantity(item.getQuantity());
            response.setPriceAtOrder(item.getPriceAtOrder());
            return response;
        }).toList();
    }
}
