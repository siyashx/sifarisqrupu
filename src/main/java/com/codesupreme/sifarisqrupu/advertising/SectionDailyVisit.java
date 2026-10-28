package com.codesupreme.sifarisqrupu.advertising;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Aggregate page entries, shared by both mobile applications. No per-entry personal data. */
@Entity
@Getter
@Setter
@Table(name = "community_section_daily_visit", indexes = @Index(columnList = "visit_day"))
public class SectionDailyVisit {
    @Id @Column(length = 24) private String id;
    @Column(name = "visit_day", nullable = false, length = 10) private String visitDay;
    @Column(nullable = false, length = 8) private String section;
    @Column(name = "entry_count", nullable = false) private long entryCount;
}
