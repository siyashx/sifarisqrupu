package com.codesupreme.sifarisqrupu.dto.mototaxi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class MotoTaxiPricingDto {

    private BigDecimal manMinimumPrice;
    private BigDecimal manPricePerKm;
    private BigDecimal womanMinimumPrice;
    private BigDecimal womanPricePerKm;
    private BigDecimal deliveryMinimumPrice;
    private BigDecimal deliveryPricePerKm;
    private BigDecimal dispatchRadiusKm;
}
