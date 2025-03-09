package com.codesupreme.sifarisqrupu.dto.user;

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
public class UserDto {

    private Long id;
    private String oneSignal;
    private String userType;
    private String name;
    private String phoneNumber;
    private List<Long> notificationHistory;
    private Boolean isMutedNotifications;
    private String password;
    private Integer orderCount;
    private List<String> identifyPhoto;
    private String courierStatus;
    private String customerStatus;
    private Boolean online;
    private String activeDeviceId;
    private String expiryDate;
    private String createdDate;
    private String isRequest;
    @JsonProperty("isDisable")
    private Boolean isDisable;

}
