package com.codesupreme.sifarisqrupu.model.mototaxi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Table(name = "mototaxi_pricing")
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class MotoTaxiPricing {

    @Id
    private Long id;

    @Column(name = "man_minimum_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal manMinimumPrice;

    @Column(name = "man_price_per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal manPricePerKm;

    @Column(name = "woman_minimum_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal womanMinimumPrice;

    @Column(name = "woman_price_per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal womanPricePerKm;

    @Column(name = "delivery_minimum_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryMinimumPrice;

    @Column(name = "delivery_price_per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryPricePerKm;
}
