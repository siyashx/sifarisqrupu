package com.codesupreme.sifarisqrupu.dto.superadmin;

import lombok.Data;

@Data
public class WhatsappBridgeGroupSyncItem {
    private String groupJid;
    private String groupName;
    private Boolean enabledByDefault;
}
