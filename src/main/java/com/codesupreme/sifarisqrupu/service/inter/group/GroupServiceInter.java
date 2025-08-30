package com.codesupreme.sifarisqrupu.service.inter.group;

import com.codesupreme.sifarisqrupu.dto.group.GroupDto;

import java.util.List;

public interface GroupServiceInter {

    // Bütün grouplari əldə et
    List<GroupDto> getAllGroups();

    // ID-yə görə group əldə et
    GroupDto getGroupById(Long id);

    // Yeni group əlavə et
    GroupDto saveGroup(GroupDto dto);

    // Mövcud groupu yenilə
    GroupDto updateGroup(Long groupId, GroupDto groupDto);

    // Groupu sil
    Boolean deleteGroup(Long id);
}
