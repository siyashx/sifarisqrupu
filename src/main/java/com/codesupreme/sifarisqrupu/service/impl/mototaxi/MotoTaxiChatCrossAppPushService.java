package com.codesupreme.sifarisqrupu.service.impl.mototaxi;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.service.impl.notification_mute.NotificationMutePreferenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MotoTaxiChatCrossAppPushService {

    private static final Logger log = LoggerFactory.getLogger(MotoTaxiChatCrossAppPushService.class);

    private static final String APP_ZAKAZ = "ZAKAZ";
    private static final String APP_ELEHBER = "ELEHBER";

    private final UserRepository userRepository;
    private final NotificationMutePreferenceService mutePreferenceService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String zakazAppId;
    private final String zakazRestApiKey;
    private final String zakazAndroidChannelId;
    private final String elehberAppId;
    private final String elehberRestApiKey;
    private final String elehberAndroidChannelId;

    public MotoTaxiChatCrossAppPushService(
            UserRepository userRepository,
            NotificationMutePreferenceService mutePreferenceService,
            ObjectMapper objectMapper,
            @Value("${zakaz.onesignal.app-id:26a128f2-4ccf-4dde-9986-2b27bb1a25b2}") String zakazAppId,
            @Value("${zakaz.onesignal.rest-api-key:}") String zakazRestApiKey,
            @Value("${zakaz.onesignal.mototaxi-android-channel-id:f737e363-a9c4-4d72-a3c0-af521f13fe78}") String zakazAndroidChannelId,
            @Value("${mototaxi.onesignal.app-id:}") String elehberAppId,
            @Value("${mototaxi.onesignal.rest-api-key:}") String elehberRestApiKey,
            @Value("${mototaxi.onesignal.android-channel-id:c668a935-ea3e-450d-afa4-5853169c36cf}") String elehberAndroidChannelId
    ) {
        this.userRepository = userRepository;
        this.mutePreferenceService = mutePreferenceService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl("https://api.onesignal.com").build();
        this.zakazAppId = clean(zakazAppId);
        this.zakazRestApiKey = clean(zakazRestApiKey);
        this.zakazAndroidChannelId = clean(zakazAndroidChannelId);
        this.elehberAppId = clean(elehberAppId);
        this.elehberRestApiKey = clean(elehberRestApiKey);
        this.elehberAndroidChannelId = clean(elehberAndroidChannelId);
    }

    public Map<String, Object> sendToOppositeApp(
            String rawSourceApp,
            Long senderUserId,
            String username,
            String message
    ) {
        final String sourceApp = normalizeSourceApp(rawSourceApp);
        if (senderUserId == null || senderUserId <= 0) {
            throw new IllegalArgumentException("senderUserId must be positive");
        }

        final String cleanMessage = clean(message);
        if (cleanMessage.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }

        final String targetApp = APP_ZAKAZ.equals(sourceApp) ? APP_ELEHBER : APP_ZAKAZ;
        final List<String> recipients = loadRecipients(targetApp, senderUserId);

        if (recipients.isEmpty()) {
            return Map.of(
                    "sourceApp", sourceApp,
                    "targetApp", targetApp,
                    "recipients", 0,
                    "accepted", false,
                    "reason", "no_recipients"
            );
        }

        final String title = "🛵 Yeni Moto Taksi sifarişi!";
        // Build a stable body without coupling push success to chat delivery.
        final String safeUsername = clean(username);
        final String notificationBody = safeUsername.isBlank()
                ? "📩 " + cleanMessage
                : "📩 " + safeUsername + ": " + cleanMessage;

        final boolean accepted = APP_ELEHBER.equals(targetApp)
                ? post(
                        targetApp,
                        elehberAppId,
                        elehberRestApiKey,
                        elehberAndroidChannelId,
                        "elehber://moto-taksi-chat",
                        "/moto-taksi-chat",
                        "MotoTaksiChat",
                        recipients,
                        title,
                        notificationBody
                )
                : post(
                        targetApp,
                        zakazAppId,
                        zakazRestApiKey,
                        zakazAndroidChannelId,
                        "zakazqrupu://moto-taksi",
                        "/moto-taksi",
                        "MotoTaksi",
                        recipients,
                        title,
                        notificationBody
                );

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceApp", sourceApp);
        result.put("targetApp", targetApp);
        result.put("recipients", recipients.size());
        result.put("accepted", accepted);
        return result;
    }

    private List<String> loadRecipients(String targetApp, Long senderUserId) {
        final String muteScope = APP_ELEHBER.equals(targetApp)
                ? NotificationMutePreferenceService.SCOPE_MOTOTAXI_CHAT
                : NotificationMutePreferenceService.SCOPE_ORDER_GROUP_PUSH;
        final Set<Long> muted = new LinkedHashSet<>(
                mutePreferenceService.getEffectiveMutedUserIds(targetApp, muteScope)
        );

        final Set<String> recipients = new LinkedHashSet<>();
        for (User user : userRepository.findAll()) {
            if (user == null || user.getId() == null) continue;
            if (user.getId().equals(senderUserId)) continue;
            if (muted.contains(user.getId())) continue;

            final String userType = clean(user.getUserType()).toLowerCase(Locale.ROOT);
            if (userType.contains("customer")) continue;

            // Elehber MotoTaksi Chat notifications are courier-work alerts.
            // A courier who explicitly went offline must not receive them.
            // Keep Zakaz recipient behavior unchanged.
            if (APP_ELEHBER.equals(targetApp) && !Boolean.TRUE.equals(user.getOnline())) {
                continue;
            }

            final String prefix = APP_ELEHBER.equals(targetApp)
                    ? "elehber_user_"
                    : "zakaz_user_";
            recipients.add(prefix + user.getId());
        }

        return new ArrayList<>(recipients);
    }

    private boolean post(
            String targetApp,
            String appId,
            String restApiKey,
            String androidChannelId,
            String appUrl,
            String route,
            String screen,
            List<String> recipients,
            String title,
            String body
    ) {
        if (appId.isBlank() || restApiKey.isBlank()) {
            log.warn("MotoTaksi cross-app push skipped: {} OneSignal credentials are not configured", targetApp);
            return false;
        }

        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app_id", appId);
        payload.put("include_aliases", Map.of("external_id", recipients));
        payload.put("target_channel", "push");
        payload.put("headings", Map.of("en", title));
        payload.put("contents", Map.of("en", body));
        payload.put("priority", 10);
        if (!androidChannelId.isBlank()) {
            payload.put("android_channel_id", androidChannelId);
        }
        payload.put("app_url", appUrl);
        payload.put("data", Map.of(
                "screen", screen,
                "route", route,
                "scope", "mototaxi_chat",
                "groupId", 1
        ));

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                final String responseBody = restClient.post()
                        .uri("/notifications?c=push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Key " + restApiKey)
                        .body(payload)
                        .retrieve()
                        .body(String.class);

                if (responseBody == null || responseBody.isBlank()) {
                    log.warn("MotoTaksi cross-app push empty response. targetApp={}, attempt={}", targetApp, attempt);
                    continue;
                }

                final JsonNode root = objectMapper.readTree(responseBody);
                final String messageId = root.path("id").asText("").trim();
                if (!messageId.isBlank()) {
                    log.info(
                            "MotoTaksi cross-app push accepted. targetApp={}, recipients={}, messageId={}",
                            targetApp,
                            recipients.size(),
                            messageId
                    );
                    return true;
                }

                log.warn(
                        "MotoTaksi cross-app push created no message. targetApp={}, attempt={}, response={}",
                        targetApp,
                        attempt,
                        responseBody
                );
            } catch (RestClientResponseException error) {
                log.warn(
                        "MotoTaksi cross-app push HTTP error. targetApp={}, attempt={}, status={}, body={}",
                        targetApp,
                        attempt,
                        error.getStatusCode(),
                        error.getResponseBodyAsString()
                );
            } catch (Exception error) {
                log.warn(
                        "MotoTaksi cross-app push error. targetApp={}, attempt={}, error={}",
                        targetApp,
                        attempt,
                        error.getMessage()
                );
            }
        }

        return false;
    }

    private static String normalizeSourceApp(String rawSourceApp) {
        final String value = clean(rawSourceApp).toUpperCase(Locale.ROOT);
        if (!APP_ZAKAZ.equals(value) && !APP_ELEHBER.equals(value)) {
            throw new IllegalArgumentException("sourceApp must be ZAKAZ or ELEHBER");
        }
        return value;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
