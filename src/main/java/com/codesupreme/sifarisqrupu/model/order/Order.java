package com.codesupreme.sifarisqrupu.model.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Table(name = "`order`")
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;
    private Long courierId;

    private String orderType;

    @ElementCollection
    @CollectionTable(name = "order_from_address", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "from_address")
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<String> fromAddress;


    @ElementCollection
    @CollectionTable(name = "order_to_address", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "to_address")
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<String> toAddress;

    @ElementCollection
    @CollectionTable(name = "order_cancelled_couriers", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "cancelled_courier_id")
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<Long> cancelledCourierIds;


    private String status;
    private Double price;
    private Double distance;

    // Sequential MotoTaksi dispatch state. courierId is written only after
    // the offered courier actually accepts the order.
    private Long offeredCourierId;
    private Date offerExpiresAt;

    // Multi-courier fan-out dispatch. A courier keeps its own offer for the
    // full offer window while the next nearest courier can be invited after
    // the fan-out interval. The legacy fields above are retained for old apps.
    @ElementCollection
    @CollectionTable(name = "order_active_offers", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "courier_id")
    @Column(name = "expires_at")
    @org.hibernate.annotations.BatchSize(size = 50)
    private Map<Long, Date> activeOfferExpirations;

    private Date lastOfferAt;
    private Date searchExpiresAt;

    @JsonProperty("isDisable")
    private Boolean isDisable;

    @CreationTimestamp
    private Date createdAt;
}

