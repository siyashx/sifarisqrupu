package com.codesupreme.sifarisqrupu.model.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Table
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    private String customerStatus;
    private Boolean online;
    private String activeDeviceId;
    private String expiryDate;
    private String createdDate;
    @JsonProperty("isDisable")
    private Boolean isDisable;
}
