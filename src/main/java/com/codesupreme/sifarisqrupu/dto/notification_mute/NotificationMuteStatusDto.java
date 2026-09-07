package com.codesupreme.sifarisqrupu.dto.notification_mute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationMuteStatusDto {
    private Long userId;
    private String appCode;
    private String scope;
    private Boolean muted;
    private Boolean explicitPreference;
}
