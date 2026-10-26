package com.codesupreme.sifarisqrupu.pairing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
  name = "community_pairing",
  uniqueConstraints = @UniqueConstraint(
    columnNames = { "created_by", "request_id" }
  )
)
public class Pairing {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  private long version;

  @Column(nullable = false, length = 140)
  private String title;

  @Column(length = 4000)
  private String description;

  private int groupSize;
  private int durationSeconds = 30;

  @Column(length = 16, nullable = false)
  private String status = "ACTIVE";

  @Column(columnDefinition = "LONGTEXT", nullable = false)
  private String stateJson = "{}";

  private long createdAt;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "request_id", length = 64)
  private String requestId;

  private boolean deleted;
  private int notificationRound;
}
