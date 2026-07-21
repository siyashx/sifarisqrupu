package com.codesupreme.sifarisqrupu.service.impl.superadmin;

import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappGroupDailyStatRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappGroupUserDailyStatRepository;
import com.codesupreme.sifarisqrupu.dto.superadmin.GroupStatIncrementRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class WhatsappGroupStatService {

    private static final ZoneId BAKU_ZONE =
            ZoneId.of("Asia/Baku");

    private final WhatsappGroupDailyStatRepository groupRepository;
    private final WhatsappGroupUserDailyStatRepository userRepository;

    public WhatsappGroupStatService(
            WhatsappGroupDailyStatRepository groupRepository,
            WhatsappGroupUserDailyStatRepository userRepository
    ) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    /*
     * Qrup sayı və istifadəçi sayı eyni transaction-da artırılır.
     * Telefon yoxdursa qrup sayı artırılır, istifadəçi statistikası keçilir.
     */
    @Transactional
    public void increment(
            GroupStatIncrementRequest request
    ) {
        String instanceName =
                request.getInstanceName().trim();

        String groupJid =
                request.getGroupJid().trim();

        String groupName =
                request.getGroupName().trim();

        LocalDate today =
                LocalDate.now(BAKU_ZONE);

        groupRepository.increment(
                instanceName,
                groupJid,
                groupName,
                today
        );

        String phone =
                normalizePhone(request.getPhone());

        if (!phone.isBlank()) {
            userRepository.increment(
                    instanceName,
                    groupJid,
                    groupName,
                    phone,
                    today
            );
        }
    }

    private String normalizePhone(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String digits =
                value.replaceAll("\\D", "");

        /*
         * Beynəlxalq telefon nömrələri üçün təhlükəsiz interval.
         * LID və boş dəyərlər statistikaya düşməsin.
         */
        if (
                digits.length() < 8 ||
                digits.length() > 15
        ) {
            return "";
        }

        return digits;
    }
}
