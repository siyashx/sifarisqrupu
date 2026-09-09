package com.codesupreme.sifarisqrupu.dto.superadmin;

import lombok.Data;

@Data
public class WhatsappBridgeGroupRequest {
    private String instanceName;
    private String groupJid;
    private String groupName;
}
