package com.gumbatali.marketplace.service;

import com.gumbatali.marketplace.common.error.ApiException;
import com.gumbatali.marketplace.common.error.ErrorCode;
import com.gumbatali.marketplace.domain.model.ProductEntity;
import com.gumbatali.marketplace.domain.model.ProductStatus;
import com.gumbatali.marketplace.domain.model.UserRole;
import com.gumbatali.marketplace.domain.repository.ProductRepository;
import com.gumbatali.marketplace.generated.model.ProductCreate;
import com.gumbatali.marketplace.generated.model.ProductPageResponse;
import com.gumbatali.marketplace.generated.model.ProductResponse;
import com.gumbatali.marketplace.generated.model.ProductUpdate;
import com.gumbatali.marketplace.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ApiMapper apiMapper;

    public ProductService(ProductRepository productRepository, ApiMapper apiMapper) {
        this.productRepository = productRepository;
        this.apiMapper = apiMapper;
    }

    @Transactional
    public ProductResponse createProduct(ProductCreate request, AuthenticatedUser user) {
        // USER не может создавать товар по матрице ролей.
        if (user.role() == UserRole.USER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        ProductEntity product = new ProductEntity();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(request.getCategory());
        product.setStatus(apiMapper.toDomainProductStatus(request.getStatus()));
        product.setSellerId(user.id());

        ProductEntity saved = productRepository.save(product);
        return apiMapper.toProductResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        ProductEntity product = productRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND));
        return apiMapper.toProductResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse listProducts(Integer page,
                                            Integer size,
                                            com.gumbatali.marketplace.generated.model.ProductStatus status,
                                            String category) {
        // Пошаговая сборка фильтра: статус + категория.
        Specification<ProductEntity> spec = Specification.where(null);

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), apiMapper.toDomainProductStatus(status)));
        }
        if (category != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }

        Page<ProductEntity> result = productRepository.findAll(
            spec,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        ProductPageResponse response = new ProductPageResponse();
        response.setContent(result.getContent().stream().map(apiMapper::toProductResponse).toList());
        response.setTotalElements(result.getTotalElements());
        response.setPage(page);
        response.setSize(size);
        return response;
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductUpdate request, AuthenticatedUser user) {
        ProductEntity product = productRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND));

        verifyProductOwnership(user, product);

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(request.getCategory());
        product.setStatus(apiMapper.toDomainProductStatus(request.getStatus()));

        ProductEntity updated = productRepository.save(product);
        return apiMapper.toProductResponse(updated);
    }

    @Transactional
    public void deleteProduct(UUID id, AuthenticatedUser user) {
        ProductEntity product = productRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND));

        verifyProductOwnership(user, product);
        // Мягкое удаление: физически запись не удаляем.
        product.setStatus(ProductStatus.ARCHIVED);
        productRepository.save(product);
    }

    private void verifyProductOwnership(AuthenticatedUser user, ProductEntity product) {
        // ADMIN может всё.
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        // SELLER может менять только свои товары.
        if (user.role() == UserRole.SELLER && user.id().equals(product.getSellerId())) {
            return;
        }
        throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
}
