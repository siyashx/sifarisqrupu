package com.codesupreme.sifarisqrupu.push;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
@Table(name="push_delivery", uniqueConstraints=@UniqueConstraint(columnNames={"event_id", "device_id"}),
 indexes=@Index(name="push_delivery_due", columnList="state,next_attempt_at"))
public class PushDelivery {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="event_id", nullable=false, length=100) private String eventId;
    @Column(name="device_id", nullable=false) private Long deviceId;
    private Long userId;
    @Column(nullable=false, length=16) private String appCode;
    @Column(nullable=false, length=16) private String state;
    @Column(columnDefinition="TEXT", nullable=false) private String payload;
    private long expiresAt;
    @Column(name="next_attempt_at") private long nextAttemptAt;
    private int attempts;
    @Column(length=100) private String lastError;
}
