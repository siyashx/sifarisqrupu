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
        name = "whatsapp_bridge_instance",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_whatsapp_bridge_instance_name",
                        columnNames = "instance_name"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsappBridgeInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_name", nullable = false, length = 100)
    private String instanceName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "connected", nullable = false)
    private Boolean connected;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
