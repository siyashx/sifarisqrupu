package com.codesupreme.sifarisqrupu.service.impl.mototaxi;

import com.codesupreme.sifarisqrupu.dao.mototaxi.MotoTaxiPricingRepository;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dto.mototaxi.MotoTaxiPricingDto;
import com.codesupreme.sifarisqrupu.dto.mototaxi.MotoTaxiQuoteResponse;
import com.codesupreme.sifarisqrupu.model.mototaxi.MotoTaxiPricing;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
public class MotoTaxiPricingService {

    private static final long PRICING_ID = 1L;
    private static final BigDecimal DEFAULT_MINIMUM_PRICE = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_PRICE_PER_KM = new BigDecimal("0.60");

    private final MotoTaxiPricingRepository pricingRepository;
    private final UserRepository userRepository;

    public MotoTaxiPricingService(
            MotoTaxiPricingRepository pricingRepository,
            UserRepository userRepository
    ) {
        this.pricingRepository = pricingRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MotoTaxiPricingDto getPricing() {
        return toDto(getOrCreatePricing());
    }

    @Transactional
    public MotoTaxiPricingDto updatePricing(MotoTaxiPricingDto dto) {
        validatePricing(dto);

        MotoTaxiPricing pricing = getOrCreatePricing();
        pricing.setManMinimumPrice(money(dto.getManMinimumPrice()));
        pricing.setManPricePerKm(money(dto.getManPricePerKm()));
        pricing.setWomanMinimumPrice(money(dto.getWomanMinimumPrice()));
        pricing.setWomanPricePerKm(money(dto.getWomanPricePerKm()));
        pricing.setDeliveryMinimumPrice(money(dto.getDeliveryMinimumPrice()));
        pricing.setDeliveryPricePerKm(money(dto.getDeliveryPricePerKm()));

        return toDto(pricingRepository.save(pricing));
    }

    @Transactional
    public MotoTaxiQuoteResponse quote(Long customerId, String orderType, Double distance) {
        validateDistance(distance);

        String normalizedOrderType = normalizeOrderType(orderType);
        MotoTaxiPricing pricing = getOrCreatePricing();

        BigDecimal minimumPrice;
        BigDecimal pricePerKm;
        String pricingType;
        String gender = null;

        if ("delivery".equals(normalizedOrderType)) {
            minimumPrice = pricing.getDeliveryMinimumPrice();
            pricePerKm = pricing.getDeliveryPricePerKm();
            pricingType = "delivery";
        } else {
            if (customerId == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "customerId is required for ride pricing"
                );
            }

            User customer = userRepository.findById(customerId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Customer not found with id: " + customerId
                    ));

            gender = normalizeGender(customer.getGender());

            if ("woman".equals(gender)) {
                minimumPrice = pricing.getWomanMinimumPrice();
                pricePerKm = pricing.getWomanPricePerKm();
                pricingType = "ride_woman";
            } else {
                // Backward compatibility: old/missing gender values use man pricing.
                gender = "man";
                minimumPrice = pricing.getManMinimumPrice();
                pricePerKm = pricing.getManPricePerKm();
                pricingType = "ride_man";
            }
        }

        BigDecimal calculatedPrice = calculatePrice(distance, minimumPrice, pricePerKm);

        return MotoTaxiQuoteResponse.builder()
                .pricingType(pricingType)
                .gender(gender)
                .distance(distance)
                .minimumPrice(money(minimumPrice))
                .pricePerKm(money(pricePerKm))
                .price(calculatedPrice)
                .build();
    }

    @Transactional
    public Double calculateOrderPrice(Long customerId, String orderType, Double distance) {
        return quote(customerId, orderType, distance).getPrice().doubleValue();
    }

    private MotoTaxiPricing getOrCreatePricing() {
        return pricingRepository.findById(PRICING_ID)
                .orElseGet(() -> pricingRepository.save(defaultPricing()));
    }

    private MotoTaxiPricing defaultPricing() {
        return MotoTaxiPricing.builder()
                .id(PRICING_ID)
                .manMinimumPrice(DEFAULT_MINIMUM_PRICE)
                .manPricePerKm(DEFAULT_PRICE_PER_KM)
                .womanMinimumPrice(DEFAULT_MINIMUM_PRICE)
                .womanPricePerKm(DEFAULT_PRICE_PER_KM)
                .deliveryMinimumPrice(DEFAULT_MINIMUM_PRICE)
                .deliveryPricePerKm(DEFAULT_PRICE_PER_KM)
                .build();
    }

    private MotoTaxiPricingDto toDto(MotoTaxiPricing pricing) {
        return MotoTaxiPricingDto.builder()
                .manMinimumPrice(money(pricing.getManMinimumPrice()))
                .manPricePerKm(money(pricing.getManPricePerKm()))
                .womanMinimumPrice(money(pricing.getWomanMinimumPrice()))
                .womanPricePerKm(money(pricing.getWomanPricePerKm()))
                .deliveryMinimumPrice(money(pricing.getDeliveryMinimumPrice()))
                .deliveryPricePerKm(money(pricing.getDeliveryPricePerKm()))
                .build();
    }

    private BigDecimal calculatePrice(
            Double distance,
            BigDecimal minimumPrice,
            BigDecimal pricePerKm
    ) {
        BigDecimal variablePrice = BigDecimal.valueOf(distance)
                .multiply(pricePerKm);

        BigDecimal result = variablePrice.max(minimumPrice);
        return money(result);
    }

    private String normalizeOrderType(String orderType) {
        if (orderType == null || orderType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderType is required");
        }

        String normalized = orderType.trim().toLowerCase(Locale.ROOT);
        if (!"ride".equals(normalized) && !"delivery".equals(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "orderType must be ride or delivery"
            );
        }
        return normalized;
    }

    private String normalizeGender(String gender) {
        if (gender == null) {
            return "";
        }
        return gender.trim().toLowerCase(Locale.ROOT);
    }

    private void validateDistance(Double distance) {
        if (distance == null || !Double.isFinite(distance) || distance <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "distance must be greater than 0"
            );
        }
    }

    private void validatePricing(MotoTaxiPricingDto dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pricing body is required");
        }

        validatePositiveMoney(dto.getManMinimumPrice(), "manMinimumPrice");
        validatePositiveMoney(dto.getManPricePerKm(), "manPricePerKm");
        validatePositiveMoney(dto.getWomanMinimumPrice(), "womanMinimumPrice");
        validatePositiveMoney(dto.getWomanPricePerKm(), "womanPricePerKm");
        validatePositiveMoney(dto.getDeliveryMinimumPrice(), "deliveryMinimumPrice");
        validatePositiveMoney(dto.getDeliveryPricePerKm(), "deliveryPricePerKm");
    }

    private void validatePositiveMoney(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must be greater than 0"
            );
        }

        if (value.compareTo(new BigDecimal("10000")) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " is too large"
            );
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
