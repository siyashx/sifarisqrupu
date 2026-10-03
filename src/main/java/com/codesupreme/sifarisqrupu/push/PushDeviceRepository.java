package com.codesupreme.sifarisqrupu.push;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {
    Optional<PushDevice> findByAppCodeAndInstallationId(String appCode, String installationId);
    List<PushDevice> findByAppCodeAndUserIdInAndEnabledTrue(String appCode, Collection<Long> userIds);
    List<PushDevice> findByAppCodeAndToken(String appCode, String token);
}
