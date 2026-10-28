package com.codesupreme.sifarisqrupu.dao.elan;

import com.codesupreme.sifarisqrupu.model.elan.Elan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElanRepository extends JpaRepository<Elan, Long>  {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Elan e where e.id=:id")
    java.util.Optional<Elan> lockForPromotion(@org.springframework.data.repository.query.Param("id") Long id);

}
