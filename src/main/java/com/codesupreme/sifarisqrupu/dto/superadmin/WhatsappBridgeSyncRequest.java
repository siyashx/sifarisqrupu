package com.codesupreme.sifarisqrupu.dto.superadmin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WhatsappBridgeSyncRequest {
    private List<WhatsappBridgeInstanceSyncItem> instances = new ArrayList<>();
}
