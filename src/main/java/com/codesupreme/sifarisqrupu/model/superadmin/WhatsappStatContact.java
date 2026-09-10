package com.codesupreme.sifarisqrupu.model.superadmin;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "whatsapp_stat_contact",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_whatsapp_stat_contact_phone",
                        columnNames = {"phone"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsappStatContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    /* WhatsApp webhook-dan gələn istifadəçi profil adı. */
    @Column(name = "whatsapp_name", length = 255)
    private String whatsappName;

    /* Adminin əl ilə verdiyi qlobal ad. */
    @Column(name = "custom_name", length = 255)
    private String customName;
}
