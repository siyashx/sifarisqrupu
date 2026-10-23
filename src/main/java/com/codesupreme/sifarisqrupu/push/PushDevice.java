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
    // Installation-local UI role; shared User.userType remains a courier capability.
    @Column(length=16) private String activeRole;
    private boolean enabled;
    private long updatedAt;
    // Keep a fingerprint so a cached, unregistered token cannot be re-enabled.
    @Column(length=64) private String rejectedTokenHash;

    public void rejectToken(String value) { rejectedTokenHash=tokenHash(value); }
    public boolean isRejectedToken(String value) {
        return rejectedTokenHash!=null && rejectedTokenHash.equals(tokenHash(value));
    }
    private static String tokenHash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
}
