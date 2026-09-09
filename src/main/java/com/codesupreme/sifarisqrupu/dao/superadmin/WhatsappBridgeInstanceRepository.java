package com.codesupreme.sifarisqrupu.dao.superadmin;

import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsappBridgeInstanceRepository
        extends JpaRepository<WhatsappBridgeInstance, Long> {

    Optional<WhatsappBridgeInstance> findByInstanceName(String instanceName);

    List<WhatsappBridgeInstance> findAllByOrderByIdAsc();
}
