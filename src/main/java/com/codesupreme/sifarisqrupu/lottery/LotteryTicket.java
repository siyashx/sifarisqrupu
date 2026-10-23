package com.codesupreme.sifarisqrupu.lottery;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Getter @Setter
@Table(name="lottery_ticket",uniqueConstraints={
    @UniqueConstraint(columnNames={"lottery_id","number"}),
    @UniqueConstraint(columnNames={"lottery_id","request_id","user_id"})},
    indexes=@Index(columnList="lottery_id,user_id"))
public class LotteryTicket {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="lottery_id",nullable=false) private Long lotteryId;
    @Column(nullable=false) private int number;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="request_id",nullable=false,length=64) private String requestId;
    private Long issuedBy;
    private long createdAt;
    public String code() { return String.format(java.util.Locale.ROOT,"%05d",number); }
}
