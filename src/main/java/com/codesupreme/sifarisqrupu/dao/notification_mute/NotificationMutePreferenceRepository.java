package com.codesupreme.sifarisqrupu.dao.notification_mute;

import com.codesupreme.sifarisqrupu.model.notification_mute.NotificationMutePreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationMutePreferenceRepository
        extends JpaRepository<NotificationMutePreference, Long> {

    Optional<NotificationMutePreference> findByUserIdAndAppCodeAndScopeCode(
            Long userId,
            String appCode,
            String scopeCode
    );

    List<NotificationMutePreference> findAllByAppCodeAndScopeCode(
            String appCode,
            String scopeCode
    );
}
