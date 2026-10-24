package com.codesupreme.sifarisqrupu.push;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {
    @org.springframework.data.jpa.repository.Query("select d.deviceId from PushDelivery d where d.eventId=:eventId")
    List<Long> findDeviceIdsByEventId(@org.springframework.data.repository.query.Param("eventId") String eventId);
    long countByEventId(String eventId);
    boolean existsByEventIdAndDeviceId(String eventId, Long deviceId);
    List<PushDelivery> findTop100ByStateAndNextAttemptAtLessThanEqualOrderByIdAsc(String state, long now);
    long deleteByExpiresAtLessThan(long before);
}
