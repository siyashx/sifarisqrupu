package com.codesupreme.sifarisqrupu.dto.superadmin;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GroupOrderStatResponse {

    private String instanceName;
    private String groupJid;
    private String groupName;
    private long orderCount;
}
