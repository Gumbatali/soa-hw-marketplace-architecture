package com.gumbatali.marketplace.service;

import com.gumbatali.marketplace.common.error.ApiException;
import com.gumbatali.marketplace.common.error.ErrorCode;
import com.gumbatali.marketplace.domain.model.PromoCodeEntity;
import com.gumbatali.marketplace.domain.repository.PromoCodeRepository;
import com.gumbatali.marketplace.generated.model.PromoCodeCreateRequest;
import com.gumbatali.marketplace.generated.model.PromoCodeResponse;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final ApiMapper apiMapper;

    public PromoCodeService(PromoCodeRepository promoCodeRepository, ApiMapper apiMapper) {
        this.promoCodeRepository = promoCodeRepository;
        this.apiMapper = apiMapper;
    }

    @Transactional
    public PromoCodeResponse createPromoCode(PromoCodeCreateRequest request) {
        if (!request.getValidUntil().isAfter(request.getValidFrom())) {
            throw new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "valid_until must be after valid_from",
                Map.of("valid_until", "must_be_after_valid_from")
            );
        }

        PromoCodeEntity promoCode = new PromoCodeEntity();
        promoCode.setCode(request.getCode());
        promoCode.setDiscountType(apiMapper.toDomainDiscountType(request.getDiscountType()));
        promoCode.setDiscountValue(request.getDiscountValue());
        promoCode.setMinOrderAmount(request.getMinOrderAmount());
        promoCode.setMaxUses(request.getMaxUses());
        promoCode.setCurrentUses(0);
        promoCode.setValidFrom(request.getValidFrom());
        promoCode.setValidUntil(request.getValidUntil());
        promoCode.setActive(request.getActive());

        PromoCodeEntity saved = promoCodeRepository.save(promoCode);
        return apiMapper.toPromoCodeResponse(saved);
    }
}
