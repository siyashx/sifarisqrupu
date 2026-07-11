package com.codesupreme.sifarisqrupu.dao.superadmin;

import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappGroupDailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface WhatsappGroupDailyStatRepository
        extends JpaRepository<WhatsappGroupDailyStat, Long> {

    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO whatsapp_group_daily_stat
                    (
                        instance_name,
                        group_jid,
                        group_name,
                        stat_date,
                        order_count
                    )
                    VALUES
                    (
                        :instanceName,
                        :groupJid,
                        :groupName,
                        :statDate,
                        1
                    )
                    ON DUPLICATE KEY UPDATE
                        group_name = VALUES(group_name),
                        order_count = order_count + 1
                    """,
            nativeQuery = true
    )
    void increment(
            @Param("instanceName") String instanceName,
            @Param("groupJid") String groupJid,
            @Param("groupName") String groupName,
            @Param("statDate") LocalDate statDate
    );

    List<WhatsappGroupDailyStat>
    findByStatDateOrderByOrderCountDesc(LocalDate statDate);
}