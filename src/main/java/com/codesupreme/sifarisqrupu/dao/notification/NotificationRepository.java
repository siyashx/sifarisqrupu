package com.codesupreme.sifarisqrupu.dao.notification;

import com.codesupreme.sifarisqrupu.model.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
