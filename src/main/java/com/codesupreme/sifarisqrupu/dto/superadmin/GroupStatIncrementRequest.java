package com.codesupreme.sifarisqrupu.dto.superadmin;

import lombok.Data;

@Data
public class GroupStatIncrementRequest {

    private String instanceName;
    private String groupJid;
    private String groupName;

    /*
     * WhatsApp qrup mesajını yazan şəxsin nömrəsi.
     * Nömrə müəyyən edilməyibsə null ola bilər.
     */
    private String phone;
}
