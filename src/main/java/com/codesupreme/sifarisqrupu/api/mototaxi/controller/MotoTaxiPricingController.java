package com.codesupreme.sifarisqrupu.api.mototaxi.controller;

import com.codesupreme.sifarisqrupu.dto.mototaxi.MotoTaxiPricingDto;
import com.codesupreme.sifarisqrupu.dto.mototaxi.MotoTaxiQuoteRequest;
import com.codesupreme.sifarisqrupu.dto.mototaxi.MotoTaxiQuoteResponse;
import com.codesupreme.sifarisqrupu.service.impl.mototaxi.MotoTaxiPricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v5/mototaxi/pricing")
public class MotoTaxiPricingController {

    private final MotoTaxiPricingService pricingService;

    public MotoTaxiPricingController(MotoTaxiPricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping
    public ResponseEntity<MotoTaxiPricingDto> getPricing() {
        return ResponseEntity.ok(pricingService.getPricing());
    }

    @PutMapping
    public ResponseEntity<MotoTaxiPricingDto> updatePricing(
            @RequestBody MotoTaxiPricingDto dto
    ) {
        return ResponseEntity.ok(pricingService.updatePricing(dto));
    }

    @PostMapping("/quote")
    public ResponseEntity<MotoTaxiQuoteResponse> quote(
            @RequestBody MotoTaxiQuoteRequest request
    ) {
        MotoTaxiQuoteResponse quote = pricingService.quote(
                request.getCustomerId(),
                request.getOrderType(),
                request.getDistance()
        );
        return ResponseEntity.ok(quote);
    }
}
