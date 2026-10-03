package com.codesupreme.sifarisqrupu.push;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
@Table(name="push_device", uniqueConstraints=@UniqueConstraint(columnNames={"app_code", "installation_id"}))
public class PushDevice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="app_code", nullable=false, length=16) private String appCode;
    @Column(name="installation_id", nullable=false, length=64) private String installationId;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false, length=4096) private String token;
    @Column(nullable=false, length=16) private String platform;
    private boolean enabled;
    private long updatedAt;
}
