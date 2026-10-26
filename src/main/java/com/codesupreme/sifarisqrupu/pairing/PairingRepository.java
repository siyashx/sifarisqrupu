package com.codesupreme.sifarisqrupu.pairing;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PairingRepository extends JpaRepository<Pairing, Long> {
  List<Pairing> findByDeletedFalseOrderByCreatedAtDesc();
  Optional<Pairing> findByCreatedByAndRequestId(Long admin, String requestId);
  List<Pairing> findByStatusAndDeletedFalse(String status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Pairing p where p.id=:id")
  Optional<Pairing> lock(@Param("id") Long id);
}
