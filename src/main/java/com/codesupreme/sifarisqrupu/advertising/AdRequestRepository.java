package com.codesupreme.sifarisqrupu.advertising;
import java.util.*;import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface AdRequestRepository extends JpaRepository<AdRequest,Long> {
 long countByKindAndStatus(String kind,String status);
 Optional<AdRequest> findByUserIdAndRequestKey(Long userId,String key);
 List<AdRequest> findByStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByCreatedAtDesc(String status,long now,long after);
 List<AdRequest> findAllByOrderByCreatedAtDesc();
 List<AdRequest> findTop50ByUserIdAndKindAndChannelOrderByCreatedAtDescIdDesc(Long userId,String kind,String channel);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AdRequest a where a.id=:id") Optional<AdRequest> lock(@Param("id") Long id);
}
