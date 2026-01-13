package com.codesupreme.sifarisqrupu.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

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
    @JsonProperty("isDisable")
    private Boolean isDisable;
    private Date createdAt;
}
