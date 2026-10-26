package com.codesupreme.sifarisqrupu.lottery;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
@Entity @Getter @Setter
@Table(name="lottery",indexes=@Index(columnList="status,ends_at"),uniqueConstraints=@UniqueConstraint(columnNames={"created_by","test_request_id"}))
public class Lottery {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @Column(nullable=false,length=140) private String title;
    @Column(length=4000) private String description;
    @Column(nullable=false,length=2000) private String imageUrl;
    private long drawAt;
    @Column(precision=12,scale=2,nullable=false) private BigDecimal ticketPrice;
    private int totalTickets;
    private int soldTickets;
    @Column(nullable=false,length=16) private String status="ACTIVE";
    private int durationSeconds=30;
    private Long startedAt;
    @Column(name="ends_at") private Long endsAt;
    private Double finalRotation;
    private Integer winnerNumber;
    private Long createdBy;
    private long createdAt;
    private long updatedAt;
    private int notificationRound;
    @Column(nullable=false,columnDefinition="boolean default false") private boolean deleted;
    @Column(nullable=false,columnDefinition="boolean default false") private boolean testMode;
    private Long testUserId;
    @Column(name="test_request_id",length=64) private String testRequestId;
    private Long deletedBy;
    private Long deletedAt;
}
