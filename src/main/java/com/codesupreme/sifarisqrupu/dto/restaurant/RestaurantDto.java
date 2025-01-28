package com.codesupreme.sifarisqrupu.dto.restaurant;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class RestaurantDto {

    private Long id;
    private String oneSignal;
    private String name;
    private String phoneNumber;
    private List<Long> notificationHistory;
    private Boolean isMutedNotifications;
    private String password;
    private Integer orderCount;
    private Boolean online;
    private String expiryDate;
    private String createdDate;
    @JsonProperty("isDisable")
    private Boolean isDisable;

}
