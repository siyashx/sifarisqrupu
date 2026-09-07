package com.codesupreme.sifarisqrupu.service.impl.notification_mute;

import com.codesupreme.sifarisqrupu.dao.chat_group.ChatGroupRepository;
import com.codesupreme.sifarisqrupu.dao.notification_mute.NotificationMutePreferenceRepository;
import com.codesupreme.sifarisqrupu.dto.notification_mute.NotificationMuteStatusDto;
import com.codesupreme.sifarisqrupu.model.chat_group.ChatGroup;
import com.codesupreme.sifarisqrupu.model.notification_mute.NotificationMutePreference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class NotificationMutePreferenceService {

    public static final String APP_ZAKAZ = "ZAKAZ";
    public static final String APP_ELEHBER = "ELEHBER";
    public static final String SCOPE_ORDER_GROUP_PUSH = "ORDER_GROUP_PUSH";
    public static final String SCOPE_MOTOTAXI_CHAT = "MOTOTAXI_CHAT";

    private final NotificationMutePreferenceRepository repository;
    private final ChatGroupRepository chatGroupRepository;

    public NotificationMutePreferenceService(
            NotificationMutePreferenceRepository repository,
            ChatGroupRepository chatGroupRepository
    ) {
        this.repository = repository;
        this.chatGroupRepository = chatGroupRepository;
    }

    @Transactional(readOnly = true)
    public NotificationMuteStatusDto getStatus(Long userId, String rawAppCode, String rawScope) {
        final String appCode = normalizeAppCode(rawAppCode);
        final String scope = normalizeScope(rawScope);
        validateUserId(userId);

        final var explicit = repository.findByUserIdAndAppCodeAndScopeCode(userId, appCode, scope);
        final boolean muted = explicit
                .map(item -> Boolean.TRUE.equals(item.getMuted()))
                .orElseGet(() -> legacyFallbackMuted(userId, appCode, scope));

        return NotificationMuteStatusDto.builder()
                .userId(userId)
                .appCode(appCode)
                .scope(scope)
                .muted(muted)
                .explicitPreference(explicit.isPresent())
                .build();
    }

    @Transactional
    public NotificationMuteStatusDto setStatus(
            Long userId,
            String rawAppCode,
            String rawScope,
            Boolean muted
    ) {
        final String appCode = normalizeAppCode(rawAppCode);
        final String scope = normalizeScope(rawScope);
        validateUserId(userId);
        if (muted == null) {
            throw new IllegalArgumentException("muted is required");
        }

        final NotificationMutePreference preference = repository
                .findByUserIdAndAppCodeAndScopeCode(userId, appCode, scope)
                .orElseGet(() -> NotificationMutePreference.builder()
                        .userId(userId)
                        .appCode(appCode)
                        .scopeCode(scope)
                        .muted(Boolean.FALSE)
                        .build());

        preference.setMuted(muted);
        repository.save(preference);

        return NotificationMuteStatusDto.builder()
                .userId(userId)
                .appCode(appCode)
                .scope(scope)
                .muted(muted)
                .explicitPreference(Boolean.TRUE)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Long> getEffectiveMutedUserIds(String rawAppCode, String rawScope) {
        final String appCode = normalizeAppCode(rawAppCode);
        final String scope = normalizeScope(rawScope);

        final List<NotificationMutePreference> explicit =
                repository.findAllByAppCodeAndScopeCode(appCode, scope);

        final Map<Long, Boolean> explicitByUser = new HashMap<>();
        final Set<Long> muted = new LinkedHashSet<>();

        for (NotificationMutePreference item : explicit) {
            if (item.getUserId() == null) continue;
            final boolean isMuted = Boolean.TRUE.equals(item.getMuted());
            explicitByUser.put(item.getUserId(), isMuted);
            if (isMuted) muted.add(item.getUserId());
        }

        // Compatibility rule: the old shared chat_group.mutedUserIds list is
        // interpreted only as Zakaz Order Group mute. It is never inherited by
        // Elehber/MotoTaksi Chat. An explicit new preference always wins.
        if (APP_ZAKAZ.equals(appCode) && SCOPE_ORDER_GROUP_PUSH.equals(scope)) {
            final ChatGroup group = chatGroupRepository.findById(1L).orElse(null);
            if (group != null && group.getMutedUserIds() != null) {
                for (Long legacyUserId : group.getMutedUserIds()) {
                    if (legacyUserId == null || explicitByUser.containsKey(legacyUserId)) continue;
                    muted.add(legacyUserId);
                }
            }
        }

        return muted.stream().toList();
    }

    private boolean legacyFallbackMuted(Long userId, String appCode, String scope) {
        if (!APP_ZAKAZ.equals(appCode) || !SCOPE_ORDER_GROUP_PUSH.equals(scope)) {
            return false;
        }

        return chatGroupRepository.findById(1L)
                .map(ChatGroup::getMutedUserIds)
                .map(ids -> ids != null && ids.contains(userId))
                .orElse(false);
    }

    private String normalizeAppCode(String rawValue) {
        final String value = normalize(rawValue);
        if (!APP_ZAKAZ.equals(value) && !APP_ELEHBER.equals(value)) {
            throw new IllegalArgumentException("Unsupported appCode: " + rawValue);
        }
        return value;
    }

    private String normalizeScope(String rawValue) {
        final String value = normalize(rawValue);
        if (!SCOPE_ORDER_GROUP_PUSH.equals(value) && !SCOPE_MOTOTAXI_CHAT.equals(value)) {
            throw new IllegalArgumentException("Unsupported scope: " + rawValue);
        }
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }
}
