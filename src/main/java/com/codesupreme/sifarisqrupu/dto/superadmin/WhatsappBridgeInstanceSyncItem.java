package com.codesupreme.sifarisqrupu.dto.superadmin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WhatsappBridgeInstanceSyncItem {
    private String instanceName;
    private String phone;
    private Boolean connected;
    private List<WhatsappBridgeGroupSyncItem> groups = new ArrayList<>();
}
