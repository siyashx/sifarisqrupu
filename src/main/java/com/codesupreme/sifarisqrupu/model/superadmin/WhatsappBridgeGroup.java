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
        name = "whatsapp_bridge_group",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_whatsapp_bridge_group",
                        columnNames = {"instance_name", "group_jid"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsappBridgeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_name", nullable = false, length = 100)
    private String instanceName;

    @Column(name = "group_jid", nullable = false, length = 160)
    private String groupJid;

    @Column(name = "group_name", nullable = false, length = 255)
    private String groupName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /*
     * ORDER_GROUP -> adi Sifariş Qrupu axını (groupId=0)
     * MOTO_TAKSI  -> Moto Taksi axını (groupId=1 + Moto Taksi push channel)
     *
     * nullable saxlanılır ki mövcud DB-də Hibernate schema-update problemsiz
     * yeni sütunu əlavə edə bilsin. Service/bridge null dəyəri təhlükəsiz
     * fallback ilə şərh edir və catalog sync mövcud sətirləri backfill edir.
     */
    @Column(name = "flow_type", length = 24)
    private String flowType;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
