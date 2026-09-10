package com.codesupreme.sifarisqrupu.dao.superadmin;

import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappStatContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WhatsappStatContactRepository
        extends JpaRepository<WhatsappStatContact, Long> {

    Optional<WhatsappStatContact> findByPhone(String phone);

    List<WhatsappStatContact> findByPhoneIn(Collection<String> phones);

    @Transactional
    @Modifying
    @Query(
            value = """
                    INSERT INTO whatsapp_stat_contact
                    (phone, whatsapp_name, custom_name)
                    VALUES (:phone, :whatsappName, NULL)
                    ON DUPLICATE KEY UPDATE
                        whatsapp_name = CASE
                            WHEN VALUES(whatsapp_name) IS NULL
                              OR TRIM(VALUES(whatsapp_name)) = ''
                            THEN whatsapp_name
                            ELSE VALUES(whatsapp_name)
                        END
                    """,
            nativeQuery = true
    )
    void upsertWhatsappName(
            @Param("phone") String phone,
            @Param("whatsappName") String whatsappName
    );
}
