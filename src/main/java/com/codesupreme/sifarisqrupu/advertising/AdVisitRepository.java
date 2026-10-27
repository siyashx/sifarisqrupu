package com.codesupreme.sifarisqrupu.advertising;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface AdVisitRepository extends JpaRepository<AdVisit,Long> {
 long countByVisitDay(String day);
 @Modifying @Query(value="INSERT IGNORE INTO community_ad_visit (visit_day,user_id) VALUES (:day,:user)",nativeQuery=true)
 int recordVisit(@Param("day")String day,@Param("user")Long user);
}
