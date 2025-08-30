package com.codesupreme.sifarisqrupu.dto.group;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupDto {

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
