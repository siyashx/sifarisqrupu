package com.codesupreme.sifarisqrupu.dto.mototaxi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class MotoTaxiQuoteRequest {

    private Long customerId;
    private String orderType;
    private Double distance;
}
