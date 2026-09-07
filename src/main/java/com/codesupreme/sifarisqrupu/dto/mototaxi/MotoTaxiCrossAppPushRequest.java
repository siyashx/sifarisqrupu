package com.codesupreme.sifarisqrupu.dto.mototaxi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MotoTaxiCrossAppPushRequest {
    private String sourceApp;
    private Long senderUserId;
    private String username;
    private String message;
}
