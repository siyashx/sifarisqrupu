package com.codesupreme.sifarisqrupu.dao.superadmin;

import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeBlockedPhone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsappBridgeBlockedPhoneRepository
        extends JpaRepository<WhatsappBridgeBlockedPhone, Long> {

    Optional<WhatsappBridgeBlockedPhone> findByInstanceNameAndGroupJidAndPhone(
            String instanceName,
            String groupJid,
            String phone
    );

    List<WhatsappBridgeBlockedPhone> findByInstanceNameAndGroupJidOrderByCreatedAtDesc(
            String instanceName,
            String groupJid
    );

    List<WhatsappBridgeBlockedPhone> findAllByOrderByCreatedAtDesc();
}
