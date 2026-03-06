package com.gumbatali.marketplace.web;

import com.gumbatali.marketplace.generated.api.OrdersApi;
import com.gumbatali.marketplace.generated.model.OrderCreateRequest;
import com.gumbatali.marketplace.generated.model.OrderPageResponse;
import com.gumbatali.marketplace.generated.model.OrderResponse;
import com.gumbatali.marketplace.generated.model.OrderStatus;
import com.gumbatali.marketplace.generated.model.OrderStatusUpdateRequest;
import com.gumbatali.marketplace.generated.model.OrderUpdateRequest;
import com.gumbatali.marketplace.security.SecurityUtils;
import com.gumbatali.marketplace.service.OrderService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrdersController implements OrdersApi {

    private final OrderService orderService;

    public OrdersController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<OrderResponse> createOrder(OrderCreateRequest orderCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.createOrder(orderCreateRequest, SecurityUtils.currentUser()));
    }

    @Override
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<OrderPageResponse> listOrders(Integer page, Integer size, OrderStatus status) {
        return ResponseEntity.ok(orderService.listOrders(page, size, status, SecurityUtils.currentUser()));
    }

    @Override
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<OrderResponse> getOrderById(UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id, SecurityUtils.currentUser()));
    }

    @Override
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<OrderResponse> updateOrder(UUID id, OrderUpdateRequest orderUpdateRequest) {
        return ResponseEntity.ok(orderService.updateOrder(id, orderUpdateRequest, SecurityUtils.currentUser()));
    }

    @Override
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<OrderResponse> cancelOrder(UUID id) {
        return ResponseEntity.ok(orderService.cancelOrder(id, SecurityUtils.currentUser()));
    }

    @Override
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<OrderResponse> updateOrderStatus(UUID id, OrderStatusUpdateRequest orderStatusUpdateRequest) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, orderStatusUpdateRequest, SecurityUtils.currentUser()));
    }
}
