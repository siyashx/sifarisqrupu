package com.codesupreme.sifarisqrupu.service.impl.superadmin;

import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappBridgeBlockedPhoneRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappBridgeGroupRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappBridgeInstanceRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappGroupDailyStatRepository;
import com.codesupreme.sifarisqrupu.dto.superadmin.*;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeBlockedPhone;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeGroup;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class WhatsappBridgeConfigService {

    private static final ZoneId BAKU_ZONE = ZoneId.of("Asia/Baku");

    private final WhatsappBridgeInstanceRepository instanceRepository;
    private final WhatsappBridgeGroupRepository groupRepository;
    private final WhatsappBridgeBlockedPhoneRepository blockedPhoneRepository;
    private final WhatsappGroupDailyStatRepository groupStatRepository;

    public WhatsappBridgeConfigService(
            WhatsappBridgeInstanceRepository instanceRepository,
            WhatsappBridgeGroupRepository groupRepository,
            WhatsappBridgeBlockedPhoneRepository blockedPhoneRepository,
            WhatsappGroupDailyStatRepository groupStatRepository
    ) {
        this.instanceRepository = instanceRepository;
        this.groupRepository = groupRepository;
        this.blockedPhoneRepository = blockedPhoneRepository;
        this.groupStatRepository = groupStatRepository;
    }

    @Transactional
    public void sync(WhatsappBridgeSyncRequest request) {
        if (request == null || request.getInstances() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (WhatsappBridgeInstanceSyncItem item : request.getInstances()) {
            String instanceName = clean(item.getInstanceName());
            if (instanceName.isBlank()) continue;

            WhatsappBridgeInstance instance = instanceRepository
                    .findByInstanceName(instanceName)
                    .orElseGet(() -> WhatsappBridgeInstance.builder()
                            .instanceName(instanceName)
                            .connected(false)
                            .build());

            instance.setPhone(normalizePhone(item.getPhone()));
            instance.setConnected(Boolean.TRUE.equals(item.getConnected()));
            instance.setLastSeenAt(now);
            instanceRepository.save(instance);

            if (item.getGroups() == null) continue;

            for (WhatsappBridgeGroupSyncItem groupItem : item.getGroups()) {
                String groupJid = clean(groupItem.getGroupJid());
                if (!isGroupJid(groupJid)) continue;

                WhatsappBridgeGroup group = groupRepository
                        .findByInstanceNameAndGroupJid(instanceName, groupJid)
                        .orElse(null);

                if (group == null) {
                    group = WhatsappBridgeGroup.builder()
                            .instanceName(instanceName)
                            .groupJid(groupJid)
                            .enabled(Boolean.TRUE.equals(groupItem.getEnabledByDefault()))
                            .build();
                }

                String groupName = clean(groupItem.getGroupName());
                group.setGroupName(groupName.isBlank() ? groupJid : groupName);
                group.setLastSeenAt(now);
                groupRepository.save(group);

                if (Boolean.TRUE.equals(group.getEnabled())) {
                    ensureStatisticsRow(group);
                }
            }
        }
    }

    public List<WhatsappBridgeInstance> getInstances() {
        return instanceRepository.findAllByOrderByIdAsc();
    }

    public List<WhatsappBridgeGroup> getGroups(
            String instanceName,
            boolean enabledOnly
    ) {
        String normalizedInstance = clean(instanceName);
        if (enabledOnly) {
            return groupRepository
                    .findByInstanceNameAndEnabledTrueOrderByGroupNameAsc(normalizedInstance);
        }
        return groupRepository.findByInstanceNameOrderByGroupNameAsc(normalizedInstance);
    }

    public List<WhatsappBridgeGroup> getRuntimeGroups() {
        return groupRepository.findByEnabledTrueOrderByInstanceNameAscGroupNameAsc();
    }

    @Transactional
    public WhatsappBridgeGroup addOrEnableGroup(WhatsappBridgeGroupRequest request) {
        String instanceName = requireText(request == null ? null : request.getInstanceName(), "instanceName");
        String groupJid = requireGroupJid(request == null ? null : request.getGroupJid());
        String groupName = clean(request == null ? null : request.getGroupName());

        WhatsappBridgeGroup group = groupRepository
                .findByInstanceNameAndGroupJid(instanceName, groupJid)
                .orElseGet(() -> WhatsappBridgeGroup.builder()
                        .instanceName(instanceName)
                        .groupJid(groupJid)
                        .build());

        if (!groupName.isBlank()) {
            group.setGroupName(groupName);
        } else if (group.getGroupName() == null || group.getGroupName().isBlank()) {
            group.setGroupName(groupJid);
        }

        group.setEnabled(true);
        group.setLastSeenAt(LocalDateTime.now());
        WhatsappBridgeGroup saved = groupRepository.save(group);
        ensureStatisticsRow(saved);
        return saved;
    }

    @Transactional
    public WhatsappBridgeGroup setGroupEnabled(Long id, boolean enabled) {
        WhatsappBridgeGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Qrup tapılmadı"));
        group.setEnabled(enabled);
        WhatsappBridgeGroup saved = groupRepository.save(group);
        if (enabled) {
            ensureStatisticsRow(saved);
        }
        return saved;
    }

    public List<WhatsappBridgeBlockedPhone> getBlockedPhones(
            String instanceName,
            String groupJid
    ) {
        return blockedPhoneRepository
                .findByInstanceNameAndGroupJidOrderByCreatedAtDesc(
                        clean(instanceName),
                        clean(groupJid)
                );
    }

    public List<WhatsappBridgeBlockedPhone> getRuntimeBlockedPhones() {
        return blockedPhoneRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public WhatsappBridgeBlockedPhone blockPhone(
            WhatsappBridgeBlockedPhoneRequest request
    ) {
        String instanceName = requireText(request == null ? null : request.getInstanceName(), "instanceName");
        String groupJid = requireGroupJid(request == null ? null : request.getGroupJid());
        String phone = normalizePhone(request == null ? null : request.getPhone());

        if (phone.length() < 8 || phone.length() > 15) {
            throw new IllegalArgumentException("Telefon nömrəsi düzgün deyil");
        }

        return blockedPhoneRepository
                .findByInstanceNameAndGroupJidAndPhone(instanceName, groupJid, phone)
                .orElseGet(() -> blockedPhoneRepository.save(
                        WhatsappBridgeBlockedPhone.builder()
                                .instanceName(instanceName)
                                .groupJid(groupJid)
                                .phone(phone)
                                .createdAt(LocalDateTime.now())
                                .build()
                ));
    }

    @Transactional
    public void unblockPhone(Long id) {
        if (!blockedPhoneRepository.existsById(id)) {
            throw new IllegalArgumentException("Bloklanmış nömrə tapılmadı");
        }
        blockedPhoneRepository.deleteById(id);
    }

    private void ensureStatisticsRow(WhatsappBridgeGroup group) {
        groupStatRepository.ensureGroupRow(
                group.getInstanceName(),
                group.getGroupJid(),
                group.getGroupName(),
                LocalDate.now(BAKU_ZONE)
        );
    }

    private String requireText(String value, String field) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(field + " vacibdir");
        }
        return cleaned;
    }

    private String requireGroupJid(String value) {
        String groupJid = requireText(value, "groupJid");
        if (!isGroupJid(groupJid)) {
            throw new IllegalArgumentException("groupJid @g.us ilə bitməlidir");
        }
        return groupJid;
    }

    private boolean isGroupJid(String value) {
        return value != null && value.endsWith("@g.us");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizePhone(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");

        if (digits.startsWith("0") && digits.length() == 10) {
            return "994" + digits.substring(1);
        }

        if (digits.length() == 9) {
            return "994" + digits;
        }

        return digits;
    }
}
