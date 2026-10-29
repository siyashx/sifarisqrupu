package com.codesupreme.sifarisqrupu.advertising;
import java.util.*;import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface AdRequestRepository extends JpaRepository<AdRequest,Long> {
 @Query("select a from AdRequest a where a.userId=:user and a.kind='SHOP' and a.listingId=:listing and (a.status='PENDING' or (a.status='APPROVED' and a.endsAt>:now)) order by a.createdAt desc, a.id desc")
 List<AdRequest> openPromotions(@Param("user") Long user,@Param("listing") Long listing,@Param("now") long now);
 long countByKindAndStatus(String kind,String status);
 Optional<AdRequest> findByUserIdAndRequestKey(Long userId,String key);
 List<AdRequest> findByStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByCreatedAtDesc(String status,long now,long after);
 List<AdRequest> findAllByOrderByCreatedAtDesc();
 List<AdRequest> findTop50ByUserIdAndKindAndStatusNotOrderByCreatedAtDescIdDesc(Long userId,String kind,String excludedStatus);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AdRequest a where a.id=:id") Optional<AdRequest> lock(@Param("id") Long id);
}
