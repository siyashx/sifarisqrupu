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
        name = "whatsapp_group_user_daily_stat",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_whatsapp_group_user_daily_stat",
                        columnNames = {
                                "instance_name",
                                "group_jid",
                                "phone",
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
public class WhatsappGroupUserDailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "instance_name",
            nullable = false,
            length = 100
    )
    private String instanceName;

    @Column(
            name = "group_jid",
            nullable = false,
            length = 150
    )
    private String groupJid;

    @Column(
            name = "group_name",
            nullable = false,
            length = 255
    )
    private String groupName;

    /*
     * + işarəsi olmadan saxlanılır:
     * 994501112233
     */
    @Column(
            name = "phone",
            nullable = false,
            length = 20
    )
    private String phone;

    @Column(
            name = "stat_date",
            nullable = false
    )
    private LocalDate statDate;

    @Column(
            name = "message_count",
            nullable = false
    )
    private Integer messageCount;
}
