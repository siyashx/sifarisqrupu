package com.codesupreme.sifarisqrupu.dao.order;

import com.codesupreme.sifarisqrupu.model.order.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT o.id
        FROM Order o
        WHERE o.status = :status
          AND o.isDisable = false
        ORDER BY o.createdAt ASC, o.id ASC
    """)
    List<Long> findOpenDispatchOrderIds(@Param("status") String status);

    @Query("""
        SELECT DISTINCT o.courierId
        FROM Order o
        WHERE o.isDisable = false
          AND o.courierId IS NOT NULL
          AND o.status IN :statuses
    """)
    List<Long> findActiveCourierIds(@Param("statuses") Collection<String> statuses);

    @Query("""
        SELECT DISTINCT o.offeredCourierId
        FROM Order o
        WHERE o.isDisable = false
          AND o.status = :status
          AND o.offeredCourierId IS NOT NULL
          AND o.offerExpiresAt > :now
    """)
    List<Long> findCurrentlyOfferedCourierIds(
            @Param("status") String status,
            @Param("now") Date now
    );
}
