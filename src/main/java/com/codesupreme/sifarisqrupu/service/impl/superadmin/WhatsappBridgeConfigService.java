package com.codesupreme.sifarisqrupu.service.impl.superadmin;

import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappBridgeBlockedPhoneRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappBridgeGroupRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappBridgeInstanceRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappGroupDailyStatRepository;
import com.codesupreme.sifarisqrupu.dto.superadmin.*;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeBlockedPhone;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeGroup;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeInstance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WhatsappBridgeConfigService {

    private static final ZoneId BAKU_ZONE = ZoneId.of("Asia/Baku");

    private final WhatsappBridgeInstanceRepository instanceRepository;
    private final WhatsappBridgeGroupRepository groupRepository;
    private final WhatsappBridgeBlockedPhoneRepository blockedPhoneRepository;
    private final WhatsappGroupDailyStatRepository groupStatRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String evolutionApiBase;
    private final String evolutionApiKey;

    public WhatsappBridgeConfigService(
            WhatsappBridgeInstanceRepository instanceRepository,
            WhatsappBridgeGroupRepository groupRepository,
            WhatsappBridgeBlockedPhoneRepository blockedPhoneRepository,
            WhatsappGroupDailyStatRepository groupStatRepository,
            ObjectMapper objectMapper,
            @Value("${evolution.api.base:http://127.0.0.1:18080}") String evolutionApiBase,
            @Value("${evolution.api.key:}") String evolutionApiKey
    ) {
        this.instanceRepository = instanceRepository;
        this.groupRepository = groupRepository;
        this.blockedPhoneRepository = blockedPhoneRepository;
        this.groupStatRepository = groupStatRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.evolutionApiBase = clean(evolutionApiBase).replaceAll("/+$", "");
        this.evolutionApiKey = clean(evolutionApiKey);
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

    @Transactional
    public List<WhatsappEvolutionGroupResponse> discoverEvolutionGroups(String instanceName) {
        String normalizedInstance = requireText(instanceName, "instanceName");

        if (instanceRepository.findByInstanceName(normalizedInstance).isEmpty()) {
            throw new IllegalArgumentException("Evolution instance tapılmadı");
        }

        List<WhatsappEvolutionGroupResponse> cachedGroups =
                getCachedEvolutionGroups(normalizedInstance);

        /*
         * Admin picker üçün Evolution canlı sorğusu best-effort-dur.
         * wa-bridge onsuz da qrup kataloqunu periodik olaraq DB-yə sync edir.
         * Ona görə Evolution qısa müddətlik cavab verməsə belə son uğurlu
         * kataloqu qaytarırıq və admin ekranını boş/xəta vəziyyətinə salmırıq.
         */
        if (evolutionApiBase.isBlank() || evolutionApiKey.isBlank()) {
            if (!cachedGroups.isEmpty()) {
                return cachedGroups;
            }
            throw new IllegalStateException(
                    "Evolution API bağlantısı backend üçün konfiqurasiya edilməyib"
            );
        }

        IllegalStateException lastError = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                List<WhatsappEvolutionGroupResponse> liveGroups =
                        fetchEvolutionGroupsOnce(normalizedInstance);

                if (!liveGroups.isEmpty()) {
                    cacheEvolutionGroups(normalizedInstance, liveGroups);
                    return liveGroups;
                }

                if (!cachedGroups.isEmpty()) {
                    return cachedGroups;
                }

                return liveGroups;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                if (!cachedGroups.isEmpty()) {
                    return cachedGroups;
                }
                throw new IllegalStateException(
                        "Evolution API sorğusu dayandırıldı",
                        error
                );
            } catch (Exception error) {
                lastError = error instanceof IllegalStateException
                        ? (IllegalStateException) error
                        : new IllegalStateException(
                                "Evolution API-dən qrupları almaq mümkün olmadı",
                                error
                        );

                if (attempt < 3) {
                    try {
                        Thread.sleep(300L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        if (!cachedGroups.isEmpty()) {
                            return cachedGroups;
                        }
                        throw new IllegalStateException(
                                "Evolution API sorğusu dayandırıldı",
                                interrupted
                        );
                    }
                }
            }
        }

        if (!cachedGroups.isEmpty()) {
            return cachedGroups;
        }

        throw lastError != null
                ? lastError
                : new IllegalStateException(
                        "Evolution API-dən qrupları almaq mümkün olmadı"
                );
    }

    private List<WhatsappEvolutionGroupResponse> fetchEvolutionGroupsOnce(
            String normalizedInstance
    ) throws Exception {
        String encodedInstance = URLEncoder
                .encode(normalizedInstance, StandardCharsets.UTF_8)
                .replace("+", "%20");
        URI uri = URI.create(
                evolutionApiBase
                        + "/group/fetchAllGroups/"
                        + encodedInstance
                        + "?getParticipants=false"
        );

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("apikey", evolutionApiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Evolution API qrupları qaytarmadı (HTTP "
                            + response.statusCode()
                            + ")"
            );
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode groupArray = extractGroupArray(root);
        Map<String, WhatsappEvolutionGroupResponse> uniqueGroups =
                new LinkedHashMap<>();

        if (groupArray != null && groupArray.isArray()) {
            for (JsonNode node : groupArray) {
                String groupJid = firstText(
                        node,
                        "id", "groupJid", "jid", "remoteJid", "groupId"
                );
                if (!isGroupJid(groupJid)) continue;

                String groupName = firstText(
                        node,
                        "subject", "name", "groupName", "pushName"
                );
                if (groupName.isBlank()) {
                    groupName = "WhatsApp qrupu";
                }

                uniqueGroups.putIfAbsent(
                        groupJid,
                        new WhatsappEvolutionGroupResponse(groupJid, groupName)
                );
            }
        }

        List<WhatsappEvolutionGroupResponse> groups =
                new ArrayList<>(uniqueGroups.values());
        groups.sort(Comparator.comparing(
                WhatsappEvolutionGroupResponse::groupName,
                String.CASE_INSENSITIVE_ORDER
        ));
        return groups;
    }

    private List<WhatsappEvolutionGroupResponse> getCachedEvolutionGroups(
            String instanceName
    ) {
        return groupRepository
                .findByInstanceNameOrderByGroupNameAsc(instanceName)
                .stream()
                .filter(group -> isGroupJid(group.getGroupJid()))
                .map(group -> new WhatsappEvolutionGroupResponse(
                        group.getGroupJid(),
                        clean(group.getGroupName()).isBlank()
                                ? "WhatsApp qrupu"
                                : clean(group.getGroupName())
                ))
                .toList();
    }

    private void cacheEvolutionGroups(
            String instanceName,
            List<WhatsappEvolutionGroupResponse> groups
    ) {
        LocalDateTime now = LocalDateTime.now();

        for (WhatsappEvolutionGroupResponse item : groups) {
            String groupJid = clean(item.groupJid());
            if (!isGroupJid(groupJid)) continue;

            WhatsappBridgeGroup group = groupRepository
                    .findByInstanceNameAndGroupJid(instanceName, groupJid)
                    .orElseGet(() -> WhatsappBridgeGroup.builder()
                            .instanceName(instanceName)
                            .groupJid(groupJid)
                            .enabled(false)
                            .build());

            String groupName = clean(item.groupName());
            group.setGroupName(
                    groupName.isBlank() ? "WhatsApp qrupu" : groupName
            );
            if (group.getEnabled() == null) {
                group.setEnabled(false);
            }
            group.setLastSeenAt(now);
            groupRepository.save(group);
        }
    }

    private JsonNode extractGroupArray(JsonNode root) {
        if (root == null || root.isNull()) return null;
        if (root.isArray()) return root;

        JsonNode data = root.get("data");
        if (data != null && data.isArray()) return data;

        JsonNode groups = root.get("groups");
        if (groups != null && groups.isArray()) return groups;

        if (data != null && data.isObject()) {
            JsonNode nestedGroups = data.get("groups");
            if (nestedGroups != null && nestedGroups.isArray()) return nestedGroups;
        }

        JsonNode response = root.get("response");
        if (response != null && response.isArray()) return response;
        if (response != null && response.isObject()) {
            JsonNode nestedGroups = response.get("groups");
            if (nestedGroups != null && nestedGroups.isArray()) return nestedGroups;
        }

        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) return "";
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull() || value.isContainerNode()) continue;
            String text = clean(value.asText());
            if (!text.isBlank()) return text;
        }
        return "";
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
