package com.codesupreme.sifarisqrupu.advertising;
import jakarta.persistence.*;import lombok.*;import java.math.BigDecimal;
@Entity @Getter @Setter @Table(name="community_ad_plan") public class AdPlan {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private long version;
 @Column(nullable=false,length=8) private String kind;
 @Column(nullable=false,length=100) private String label;
 private int durationHours;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal price;
 private Long availableFrom,availableUntil;
 private boolean enabled=true;
}
