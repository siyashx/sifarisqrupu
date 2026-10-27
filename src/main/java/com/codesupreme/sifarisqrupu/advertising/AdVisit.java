package com.codesupreme.sifarisqrupu.advertising;
import jakarta.persistence.*;import lombok.*;
@Entity @Getter @Setter @Table(name="community_ad_visit",uniqueConstraints=@UniqueConstraint(columnNames={"visit_day","user_id"})) public class AdVisit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="visit_day",nullable=false,length=10) private String visitDay;
 @Column(name="user_id",nullable=false) private Long userId;
}
