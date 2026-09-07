package com.codesupreme.sifarisqrupu.model.notification_mute;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "notification_mute_preference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_mute_user_app_scope",
                columnNames = {"user_id", "app_code", "scope_code"}
        )
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class NotificationMutePreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "app_code", nullable = false, length = 32)
    private String appCode;

    @Column(name = "scope_code", nullable = false, length = 64)
    private String scopeCode;

    @Column(nullable = false)
    private Boolean muted;
}
