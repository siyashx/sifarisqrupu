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
public class MotoTaxiQuoteResponse {

    private String pricingType;
    private String gender;
    private Double distance;
    private BigDecimal minimumPrice;
    private BigDecimal pricePerKm;
    private BigDecimal price;
}
