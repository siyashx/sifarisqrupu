package com.codesupreme.sifarisqrupu.advertising;
import jakarta.persistence.*;import lombok.*;import java.math.BigDecimal;
@Entity @Getter @Setter @Table(name="community_ad_request",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","request_key"}),indexes=@Index(columnList="status,kind,starts_at,ends_at")) public class AdRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private long version;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(name="request_key",length=64,nullable=false) private String requestKey;
 private Long planId,listingId;
 @Column(nullable=false,length=8) private String kind;
 @Column(length=8) private String channel;
 @Column(nullable=false,length=140) private String title;
 @Column(length=4000) private String description;
 @Column(length=2000) private String imageUrl;
 @Column(length=2000) private String target;
 @Column(length=12) private String targetType;
 @Column(length=12,nullable=false) private String status="PENDING";
 private String planLabel;
 private int durationHours;
 @Column(precision=12,scale=2) private BigDecimal price;
 private long createdAt,requestedStart;
 @Column(name="starts_at") private Long startsAt;
 @Column(name="ends_at") private Long endsAt;
}
