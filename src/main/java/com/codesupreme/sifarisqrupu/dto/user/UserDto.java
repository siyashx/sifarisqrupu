package com.codesupreme.sifarisqrupu.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;
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
    private List<String> location;
    private String locationUrl;
    private List<String> identifyPhoto;
    private String courierStatus;
    private Boolean online;
    private String activeDeviceId;
    private Boolean isSub;
    @Column(name = "expiry_date")
    private Date expiryDate;
    private String createdDate;
    private String disableExpiryDate;
    @JsonProperty("isDisable")
    private Boolean isDisable;

}
