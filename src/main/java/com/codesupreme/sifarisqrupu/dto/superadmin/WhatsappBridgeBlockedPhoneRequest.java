package com.codesupreme.sifarisqrupu.dto.superadmin;

import lombok.Data;

@Data
public class WhatsappBridgeBlockedPhoneRequest {
    private String instanceName;
    private String groupJid;
    private String phone;
}
