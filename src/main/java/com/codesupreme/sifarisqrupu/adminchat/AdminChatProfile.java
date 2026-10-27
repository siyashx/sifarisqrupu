package com.codesupreme.sifarisqrupu.adminchat;

import java.util.List;

/** Only the fields needed for the seen-by avatars; no credentials or user history. */
public record AdminChatProfile(Long id, String name, List<String> photos) {}
