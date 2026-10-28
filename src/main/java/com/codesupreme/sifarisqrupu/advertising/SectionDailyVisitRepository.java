package com.codesupreme.sifarisqrupu.advertising;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SectionDailyVisitRepository extends JpaRepository<SectionDailyVisit, String> {
    List<SectionDailyVisit> findByVisitDay(String day);

    // An atomic database increment prevents lost entries when different devices arrive together.
    @Modifying
    @Query(value = "INSERT INTO community_section_daily_visit (id,visit_day,section,entry_count) " +
        "VALUES (:id,:day,:section,1) ON DUPLICATE KEY UPDATE entry_count=entry_count+1", nativeQuery = true)
    int increment(@Param("id") String id, @Param("day") String day, @Param("section") String section);

    @Query("select v.entryCount from SectionDailyVisit v where v.id=:id")
    Long entryCount(@Param("id") String id);
}
