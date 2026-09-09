package com.codesupreme.sifarisqrupu.dao.superadmin;

import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappGroupUserDailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WhatsappGroupUserDailyStatRepository
        extends JpaRepository<WhatsappGroupUserDailyStat, Long> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO whatsapp_group_user_daily_stat
                    (
                        instance_name,
                        group_jid,
                        group_name,
                        phone,
                        stat_date,
                        message_count
                    )
                    VALUES
                    (
                        :instanceName,
                        :groupJid,
                        :groupName,
                        :phone,
                        :statDate,
                        1
                    )
                    ON DUPLICATE KEY UPDATE
                        group_name = VALUES(group_name),
                        message_count = message_count + 1
                    """,
            nativeQuery = true
    )
    void increment(
            @Param("instanceName") String instanceName,
            @Param("groupJid") String groupJid,
            @Param("groupName") String groupName,
            @Param("phone") String phone,
            @Param("statDate") LocalDate statDate
    );

    List<WhatsappGroupUserDailyStat>
    findByStatDateAndInstanceNameAndGroupJidOrderByMessageCountDesc(
            LocalDate statDate,
            String instanceName,
            String groupJid
    );

    @Query(
            value = """
                    SELECT
                        phone AS phone,
                        SUM(message_count) AS messageCount
                    FROM whatsapp_group_user_daily_stat
                    WHERE stat_date = :statDate
                    GROUP BY phone
                    ORDER BY SUM(message_count) DESC
                    """,
            nativeQuery = true
    )
    List<UserMessageStatProjection>
    findOverallUserStatistics(
            @Param("statDate") LocalDate statDate
    );

    @Query(
            value = """
                    SELECT
                        phone AS phone,
                        SUM(message_count) AS messageCount
                    FROM whatsapp_group_user_daily_stat
                    WHERE stat_date BETWEEN :fromDate AND :toDate
                      AND instance_name = :instanceName
                      AND group_jid = :groupJid
                    GROUP BY phone
                    ORDER BY SUM(message_count) DESC
                    """,
            nativeQuery = true
    )
    List<UserMessageStatProjection>
    findGroupUserStatisticsBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("instanceName") String instanceName,
            @Param("groupJid") String groupJid
    );

    @Query(
            value = """
                    SELECT
                        phone AS phone,
                        SUM(message_count) AS messageCount
                    FROM whatsapp_group_user_daily_stat
                    WHERE stat_date BETWEEN :fromDate AND :toDate
                    GROUP BY phone
                    ORDER BY SUM(message_count) DESC
                    """,
            nativeQuery = true
    )
    List<UserMessageStatProjection>
    findOverallUserStatisticsBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
