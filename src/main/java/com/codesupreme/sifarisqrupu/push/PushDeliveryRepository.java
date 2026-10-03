package com.codesupreme.sifarisqrupu.push;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {
    boolean existsByEventIdAndDeviceId(String eventId, Long deviceId);
    List<PushDelivery> findTop100ByStateAndNextAttemptAtLessThanEqualOrderByIdAsc(String state, long now);
    long deleteByExpiresAtLessThan(long before);
}
