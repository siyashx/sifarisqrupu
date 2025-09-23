package com.codesupreme.sifarisqrupu.model.chat_group;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Table(name = "chat_group")
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ChatGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long adminId;

    // ---- Collections ----
    @ElementCollection
    @CollectionTable(
            name = "chat_group_other_admins",
            joinColumns = @JoinColumn(name = "chat_group_id")
    )
    @Column(name = "admin_id", nullable = false)
    @Builder.Default
    private List<Long> otherAdminIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "chat_group_joined_users",
            joinColumns = @JoinColumn(name = "chat_group_id")
    )
    @Column(name = "user_id", nullable = false)
    @Builder.Default
    private List<Long> joinedUserIds = new ArrayList<>();

    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @ElementCollection
    @CollectionTable(
            name = "chat_group_muted_users",
            joinColumns = @JoinColumn(name = "chat_group_id")
    )
    @Column(name = "muted_user_id", nullable = false)
    @Builder.Default
    private List<Long> mutedUserIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "chat_group_requests",
            joinColumns = @JoinColumn(name = "chat_group_id")
    )
    @Column(name = "request_user_id", nullable = false)
    @Builder.Default
    private List<Long> requestUserIds = new ArrayList<>();

    // ---- Flags ----
    private Boolean isPrivate;

    @JsonProperty("isDisable")
    private Boolean isDisable;

    // Tarix formatını hələlik String saxlayıram ki, migrationa ehtiyac olmasın
    private String createdAt;
}

