package com.codesupreme.sifarisqrupu.model.superadmin;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "whatsapp_bridge_blocked_phone",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_whatsapp_bridge_blocked_phone",
                        columnNames = {"instance_name", "group_jid", "phone"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsappBridgeBlockedPhone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_name", nullable = false, length = 100)
    private String instanceName;

    @Column(name = "group_jid", nullable = false, length = 160)
    private String groupJid;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
