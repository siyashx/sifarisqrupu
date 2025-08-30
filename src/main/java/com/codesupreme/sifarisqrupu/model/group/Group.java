package com.codesupreme.sifarisqrupu.model.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Table
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long adminId;
    private List<Long> otherAdminIds;
    private List<Long> joinedUserIds;
    private String name;
    private String description;
    private List<Long> mutedUserIds;
    @JsonProperty("isDisable")
    private Boolean isDisable;
    private String createdAt;
}
