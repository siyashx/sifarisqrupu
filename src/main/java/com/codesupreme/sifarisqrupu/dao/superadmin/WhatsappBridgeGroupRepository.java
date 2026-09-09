package com.codesupreme.sifarisqrupu.dao.superadmin;

import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsappBridgeGroupRepository
        extends JpaRepository<WhatsappBridgeGroup, Long> {

    Optional<WhatsappBridgeGroup> findByInstanceNameAndGroupJid(
            String instanceName,
            String groupJid
    );

    List<WhatsappBridgeGroup> findByInstanceNameOrderByGroupNameAsc(
            String instanceName
    );

    List<WhatsappBridgeGroup> findByInstanceNameAndEnabledTrueOrderByGroupNameAsc(
            String instanceName
    );

    List<WhatsappBridgeGroup> findByEnabledTrueOrderByInstanceNameAscGroupNameAsc();
}
