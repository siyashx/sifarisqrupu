package com.codesupreme.sifarisqrupu.api.group.controller;

import com.codesupreme.sifarisqrupu.dto.group.GroupDto;
import com.codesupreme.sifarisqrupu.service.impl.group.GroupServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/group")
public class GroupController {

    private final GroupServiceImpl service;

    public GroupController(GroupServiceImpl service) {
        this.service = service;
    }

    // Bütün groupləri əldə et
    @GetMapping
    public ResponseEntity<List<GroupDto>> getAllGroups() {
        List<GroupDto> all = service.getAllGroups();
        return ResponseEntity.ok(all);
    }

    // ID-ə görə group əldə et
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDto> getGroupById(@PathVariable("groupId") Long id) {
        GroupDto group = service.getGroupById(id);
        if (group != null) {
            return ResponseEntity.ok(group);
        }
        return ResponseEntity.notFound().build();
    }

    // Yeni group əlavə et
    @PostMapping
    public ResponseEntity<GroupDto> saveGroup(@RequestBody GroupDto dto) {
        GroupDto created = service.saveGroup(dto);
        return ResponseEntity.ok(created);
    }

    // Groupı yenilə
    @PutMapping("/{groupId}")
    public ResponseEntity<?> updateGroup(
            @PathVariable("groupId") Long id,
            @RequestBody GroupDto groupDto) {
        GroupDto updatedGroup = service.updateGroup(id, groupDto);
        if (updatedGroup != null) {
            return ResponseEntity.ok(updatedGroup);
        }
        return ResponseEntity.notFound().build();
    }

    // Groupı sil
    @DeleteMapping("/{groupId}")
    public ResponseEntity<String> deleteGroup(@PathVariable("groupId") Long id) {
        Boolean deleted = service.deleteGroup(id);
        if (deleted) {
            return ResponseEntity.ok("Group deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }
}
