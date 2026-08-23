package com.codesupreme.sifarisqrupu.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class OrderDto {

    private Long id;

    private Long customerId;
    private Long courierId;
    private String orderType;
    private List<String> fromAddress;
    private List<String> toAddress;
    private List<Long> cancelledCourierIds;
    private String status;
    private Double price;
    private Double distance;
    private Long offeredCourierId;
    private Date offerExpiresAt;
    private Map<Long, Date> activeOfferExpirations;
    private Date lastOfferAt;
    private Date searchExpiresAt;
    @JsonProperty("isDisable")
    private Boolean isDisable;
    private Date createdAt;
}
