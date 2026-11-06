package com.codesupreme.sifarisqrupu.adminusers;

import java.util.Date;
import java.util.List;

/** List cards never need passwords, notification history or session/device identifiers. */
public record AdminUserRow(
    Long id, String name, String phoneNumber, String userType,
    List<String> identifyPhoto, String courierStatus,
    Boolean isSub, Date expiryDate, Boolean isSubMotoTaksi, String motoTaksiExpiryDate
) {}
