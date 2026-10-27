package com.codesupreme.sifarisqrupu.adminchat;

import java.util.List;

/** Text-chat projection: never load the large WhatsApp thumbnail column. */
public record AdminChatMessage(Long id, String groupId, Long userId, String username,
    String phone, List<String> isSeenIds, String messageType, String message,
    String userType, String timestamp, Boolean isCompleted, Boolean isWebsite) {}
