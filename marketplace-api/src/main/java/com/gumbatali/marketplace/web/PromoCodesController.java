package com.gumbatali.marketplace.web;

import com.gumbatali.marketplace.generated.api.PromoCodesApi;
import com.gumbatali.marketplace.generated.model.PromoCodeCreateRequest;
import com.gumbatali.marketplace.generated.model.PromoCodeResponse;
import com.gumbatali.marketplace.service.PromoCodeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromoCodesController implements PromoCodesApi {

    private final PromoCodeService promoCodeService;

    public PromoCodesController(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    @Override
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<PromoCodeResponse> createPromoCode(PromoCodeCreateRequest promoCodeCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promoCodeService.createPromoCode(promoCodeCreateRequest));
    }
}
