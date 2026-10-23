package com.codesupreme.sifarisqrupu.lottery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface LotteryRepository extends JpaRepository<Lottery,Long> {
    List<Lottery> findByDeletedFalseOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lottery l where l.id=:id") Optional<Lottery> lock(@Param("id") Long id);
    List<Lottery> findByStatusAndEndsAtLessThanEqual(String status,long now);
}
