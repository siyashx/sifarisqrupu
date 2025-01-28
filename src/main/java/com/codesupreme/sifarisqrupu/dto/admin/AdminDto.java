package com.codesupreme.sifarisqrupu.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AdminDto {

    private Long id;
    private String oneSignal;
    private String name;
    private String phoneNumber;
    private List<Long> notificationHistory;
    private Boolean isMutedNotifications;
    private String password;
    private Boolean isRealAdmin;
    private Boolean isDisable;
}
