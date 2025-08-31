package com.codesupreme.sifarisqrupu.dto.chat_group;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatGroupDto {

    private Long id;
    private Long adminId;
    private List<Long> otherAdminIds;
    private List<Long> joinedUserIds;
    private String name;
    private String description;
    private List<Long> mutedUserIds;
    private Boolean isDisable;
    private String createdAt;
}
