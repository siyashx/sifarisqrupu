package com.codesupreme.sifarisqrupu.model.superadmin;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "whatsapp_group_daily_stat",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_whatsapp_group_daily_stat",
                        columnNames = {
                                "instance_name",
                                "group_jid",
                                "stat_date"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsappGroupDailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // sifarisqrupu və ya sifarisqrupu2
    @Column(
            name = "instance_name",
            nullable = false,
            length = 100
    )
    private String instanceName;

    // WhatsApp qrupunun unikal ID-si
    @Column(
            name = "group_jid",
            nullable = false,
            length = 150
    )
    private String groupJid;

    // React Native-də göstəriləcək ad
    @Column(
            name = "group_name",
            nullable = false,
            length = 255
    )
    private String groupName;

    // Statistikanın tarixi
    @Column(
            name = "stat_date",
            nullable = false
    )
    private LocalDate statDate;

    // Həmin gün gələn sifarişlərin sayı
    @Column(
            name = "order_count",
            nullable = false
    )
    private Integer orderCount;
}