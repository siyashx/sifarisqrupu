package com.codesupreme.sifarisqrupu.service.impl.superadmin;

import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappGroupDailyStatRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.UserMessageStatProjection;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappGroupUserDailyStatRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappStatContactRepository;
import com.codesupreme.sifarisqrupu.dto.superadmin.UserMessageStatResponse;
import com.codesupreme.sifarisqrupu.dto.superadmin.WhatsappContactNameResponse;
import com.codesupreme.sifarisqrupu.dto.superadmin.GroupStatIncrementRequest;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappGroupUserDailyStat;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappStatContact;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WhatsappGroupStatService {

    private static final ZoneId BAKU_ZONE =
            ZoneId.of("Asia/Baku");

    private final WhatsappGroupDailyStatRepository groupRepository;
    private final WhatsappGroupUserDailyStatRepository userRepository;
    private final WhatsappStatContactRepository contactRepository;
    private final WhatsappBridgeConfigService bridgeConfigService;

    public WhatsappGroupStatService(
            WhatsappGroupDailyStatRepository groupRepository,
            WhatsappGroupUserDailyStatRepository userRepository,
            WhatsappStatContactRepository contactRepository,
            WhatsappBridgeConfigService bridgeConfigService
    ) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
        this.bridgeConfigService = bridgeConfigService;
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
            String whatsappName = cleanDisplayName(
                    request.getWhatsappName(),
                    phone
            );

            if (!whatsappName.isBlank()) {
                contactRepository.upsertWhatsappName(
                        phone,
                        whatsappName
                );
            }

            userRepository.increment(
                    instanceName,
                    groupJid,
                    groupName,
                    phone,
                    today
            );
        }
    }

    @Transactional
    public WhatsappContactNameResponse updateCustomName(
            String rawPhone,
            String rawName
    ) {
        String phone = normalizePhone(rawPhone);

        if (phone.isBlank()) {
            throw new IllegalArgumentException("Telefon nömrəsi düzgün deyil");
        }

        String customName = cleanDisplayName(rawName, phone);

        WhatsappStatContact contact =
                contactRepository.findByPhone(phone)
                        .orElseGet(() ->
                                WhatsappStatContact.builder()
                                        .phone(phone)
                                        .build()
                        );

        contact.setCustomName(
                customName.isBlank()
                        ? null
                        : customName
        );

        WhatsappStatContact saved =
                contactRepository.save(contact);

        return toContactResponse(saved);
    }

    public List<UserMessageStatResponse> enrichProjectionRows(
            List<UserMessageStatProjection> rows
    ) {
        return enrichProjectionRows(rows, null);
    }

    public List<UserMessageStatResponse> enrichProjectionRows(
            List<UserMessageStatProjection> rows,
            String instanceName
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<String> phones =
                rows.stream()
                        .map(UserMessageStatProjection::getPhone)
                        .collect(Collectors.toList());

        Map<String, WhatsappStatContact> contacts =
                loadAndBackfillContacts(
                        instanceName,
                        phones
                );

        return rows.stream()
                .map(row ->
                        toUserResponse(
                                row.getPhone(),
                                row.getMessageCount(),
                                contacts.get(row.getPhone())
                        )
                )
                .collect(Collectors.toList());
    }

    public List<UserMessageStatResponse> enrichDailyRows(
            List<WhatsappGroupUserDailyStat> rows
    ) {
        return enrichDailyRows(rows, null);
    }

    public List<UserMessageStatResponse> enrichDailyRows(
            List<WhatsappGroupUserDailyStat> rows,
            String instanceName
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<String> phones =
                rows.stream()
                        .map(WhatsappGroupUserDailyStat::getPhone)
                        .collect(Collectors.toList());

        Map<String, WhatsappStatContact> contacts =
                loadAndBackfillContacts(
                        instanceName,
                        phones
                );

        return rows.stream()
                .map(row ->
                        toUserResponse(
                                row.getPhone(),
                                row.getMessageCount(),
                                contacts.get(row.getPhone())
                        )
                )
                .collect(Collectors.toList());
    }

    private Map<String, WhatsappStatContact> loadAndBackfillContacts(
            String instanceName,
            Collection<String> phones
    ) {
        Map<String, WhatsappStatContact> contacts =
                loadContacts(phones);

        if (
                instanceName == null ||
                instanceName.isBlank() ||
                phones == null ||
                phones.isEmpty()
        ) {
            return contacts;
        }

        List<String> missingWhatsappNames =
                phones.stream()
                        .filter(phone -> {
                            WhatsappStatContact contact =
                                    contacts.get(phone);

                            return contact == null ||
                                    blankToNull(
                                            contact.getWhatsappName()
                                    ) == null;
                        })
                        .distinct()
                        .collect(Collectors.toList());

        if (missingWhatsappNames.isEmpty()) {
            return contacts;
        }

        Map<String, String> resolved =
                bridgeConfigService.resolveWhatsappPushNames(
                        instanceName.trim(),
                        missingWhatsappNames
                );

        if (resolved.isEmpty()) {
            return contacts;
        }

        resolved.forEach(
                (phone, whatsappName) ->
                        contactRepository.upsertWhatsappName(
                                phone,
                                whatsappName
                        )
        );

        return loadContacts(phones);
    }

    private Map<String, WhatsappStatContact> loadContacts(
            Collection<String> phones
    ) {
        if (phones == null || phones.isEmpty()) {
            return Map.of();
        }

        return contactRepository.findByPhoneIn(phones)
                .stream()
                .collect(Collectors.toMap(
                        WhatsappStatContact::getPhone,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private UserMessageStatResponse toUserResponse(
            String phone,
            Number messageCount,
            WhatsappStatContact contact
    ) {
        String whatsappName =
                contact == null
                        ? null
                        : blankToNull(contact.getWhatsappName());

        String customName =
                contact == null
                        ? null
                        : blankToNull(contact.getCustomName());

        String displayName =
                customName != null
                        ? customName
                        : whatsappName;

        return new UserMessageStatResponse(
                phone,
                whatsappName,
                customName,
                displayName,
                messageCount == null
                        ? 0L
                        : messageCount.longValue()
        );
    }

    private WhatsappContactNameResponse toContactResponse(
            WhatsappStatContact contact
    ) {
        String whatsappName =
                blankToNull(contact.getWhatsappName());
        String customName =
                blankToNull(contact.getCustomName());

        return new WhatsappContactNameResponse(
                contact.getPhone(),
                whatsappName,
                customName,
                customName != null
                        ? customName
                        : whatsappName
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String cleanDisplayName(
            String value,
            String phone
    ) {
        if (value == null) {
            return "";
        }

        String name = value
                .replaceAll("\\s+", " ")
                .trim();

        if (name.isBlank()) {
            return "";
        }

        if (name.endsWith("@s.whatsapp.net") || name.endsWith("@lid")) {
            return "";
        }

        String nameDigits = name.replaceAll("\\D", "");
        if (!phone.isBlank() && phone.equals(nameDigits)) {
            return "";
        }

        return name.length() > 255
                ? name.substring(0, 255)
                : name;
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
