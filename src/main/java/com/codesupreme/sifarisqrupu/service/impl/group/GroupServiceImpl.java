package com.codesupreme.sifarisqrupu.service.impl.group;

import com.codesupreme.sifarisqrupu.dao.group.GroupRepository;
import com.codesupreme.sifarisqrupu.dto.group.GroupDto;
import com.codesupreme.sifarisqrupu.model.group.Group;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GroupServiceImpl {

    private final GroupRepository groupRepository;
    private final ModelMapper modelMapper;

    public GroupServiceImpl(GroupRepository groupRepository, ModelMapper modelMapper) {
        this.groupRepository = groupRepository;
        this.modelMapper = modelMapper;
    }

    //ALL
    public List<GroupDto> getAllGroups() {
        List<Group> list = groupRepository.findAll();
        return list.stream()
                .map(det -> modelMapper.map(det, GroupDto.class))
                .toList();
    }

    //ById
    public GroupDto getGroupById(Long id) {
        Optional<Group> optional = groupRepository.findById(id);
        return optional.map(det -> modelMapper.map(det, GroupDto.class)).orElse(null);
    }

    //Save
    public GroupDto saveGroup(GroupDto dto) {
        Group group = modelMapper.map(dto, Group.class);
        group = groupRepository.save(group);
        return modelMapper.map(group, GroupDto.class);
    }

    //Update
    public GroupDto updateGroup(Long groupId, GroupDto groupDto) {
        Optional<Group> optional = groupRepository.findById(groupId);
        if (optional.isPresent()) {
            Group group = optional.get();

            if (groupDto.getDescription() != null) {
                group.setDescription(groupDto.getDescription());
            }

            if (groupDto.getAdminId() != null) {
                group.setAdminId(groupDto.getAdminId());
            }

            if (groupDto.getOtherAdminIds() != null) {
                group.setOtherAdminIds(groupDto.getOtherAdminIds());
            }

            if (groupDto.getJoinedUserIds() != null) {
                group.setJoinedUserIds(groupDto.getJoinedUserIds());
            }

            if (groupDto.getName() != null) {
                group.setName(groupDto.getName());
            }

            if (groupDto.getDescription() != null) {
                group.setDescription(groupDto.getDescription());
            }

            if (groupDto.getMutedUserIds() != null) {
                group.setMutedUserIds(groupDto.getMutedUserIds());
            }

            if (groupDto.getIsDisable() != null) {
                group.setIsDisable(groupDto.getIsDisable());
            }

            if (groupDto.getCreatedAt() != null) {
                group.setCreatedAt(groupDto.getCreatedAt());
            }

            group = groupRepository.save(group);

            return modelMapper.map(group, GroupDto.class);
        }
        return null;
    }

    //Delete
    public Boolean deleteGroup(Long id) {
        Optional<Group> optional = groupRepository.findById(id);
        if (optional.isPresent()) {
            Group group = optional.get();
            groupRepository.delete(group);
            return true;
        }
        return false;
    }
}
