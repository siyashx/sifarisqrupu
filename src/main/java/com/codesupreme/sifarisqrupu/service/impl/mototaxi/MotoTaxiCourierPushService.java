package com.codesupreme.sifarisqrupu.service.impl.mototaxi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MotoTaxiCourierPushService {

    private static final Logger log = LoggerFactory.getLogger(MotoTaxiCourierPushService.class);
    private static final String EXTERNAL_ID_PREFIX = "elehber_user_";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String appId;
    private final String restApiKey;
    private final String androidChannelId;
    private final int offerTtlSeconds;

    public MotoTaxiCourierPushService(
            ObjectMapper objectMapper,
            @Value("${mototaxi.onesignal.app-id:}") String appId,
            @Value("${mototaxi.onesignal.rest-api-key:}") String restApiKey,
            @Value("${mototaxi.onesignal.android-channel-id:c668a935-ea3e-450d-afa4-5853169c36cf}") String androidChannelId,
            @Value("${mototaxi.dispatch.offer-timeout-seconds:60}") long offerTimeoutSeconds
    ) {
        this.restClient = RestClient.builder().baseUrl("https://api.onesignal.com").build();
        this.objectMapper = objectMapper;
        this.appId = clean(appId);
        this.restApiKey = clean(restApiKey);
        this.androidChannelId = clean(androidChannelId);
        this.offerTtlSeconds = (int) Math.max(1, Math.min(Integer.MAX_VALUE, offerTimeoutSeconds));
    }

    public void sendNewOrderOffer(
            Long courierId,
            String pushSubscriptionId,
            Long orderId,
            String orderType
    ) {
        if (courierId == null || orderId == null) {
            return;
        }

        boolean delivery = "delivery".equalsIgnoreCase(orderType);
        String title = delivery
                ? "Yeni ÇATDIRILMA sifarişi 📦"
                : "Yeni MotoTaksi sifarişi 🏍️";
        String body = delivery
                ? "Yeni çatdırılma sifarişi var. Paketi götürməyə tələsin!"
                : "Yeni Moto Taksi sifarişi var. Müştərini götürməyə tələsin!";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app_id", appId);
        payload.put("target_channel", "push");
        payload.put("headings", Map.of("en", title));
        payload.put("contents", Map.of("en", body));
        payload.put("priority", 10);
        payload.put("ttl", offerTtlSeconds);
        if (!androidChannelId.isBlank()) {
            payload.put("android_channel_id", androidChannelId);
        }
        payload.put("data", Map.of(
                "scope", "mototaxi",
                "event", "new_order",
                "status", "no_courier",
                "orderId", orderId.toString(),
                "orderType", orderType == null ? "ride" : orderType,
                "alertKind", "courier_new_order",
                "alertId", orderId + "_" + courierId + "_" + System.currentTimeMillis()
        ));

        postToCourier(courierId, pushSubscriptionId, payload, "new_order");
    }

    public void sendOrderStop(
            Long courierId,
            String pushSubscriptionId,
            Long orderId,
            String event,
            String status,
            boolean showCustomerCancelledMessage
    ) {
        if (courierId == null || orderId == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app_id", appId);
        payload.put("target_channel", "push");
        payload.put("priority", 10);
        payload.put("ttl", 60);
        payload.put("content_available", true);
        payload.put("data", Map.of(
                "scope", "mototaxi",
                "event", event,
                "status", status,
                "orderId", orderId.toString(),
                "controlKind", "courier_order_stop"
        ));

        if (showCustomerCancelledMessage) {
            payload.put("headings", Map.of("en", "MotoTaksi"));
            payload.put("contents", Map.of("en", "Müştəri sifarişi ləğv etdi."));
            if (!androidChannelId.isBlank()) {
                payload.put("android_channel_id", androidChannelId);
            }
        }

        postToCourier(courierId, pushSubscriptionId, payload, event);
    }

    /**
     * The backend already stores the current OneSignal subscription id synced by
     * Flutter. Use that exact device subscription first. If OneSignal reports no
     * matching subscription (empty message id) or rejects the id, fall back to the
     * stable external_id bound by OneSignal.login(). This covers both token rotation
     * and temporary alias-sync problems without sending two successful pushes.
     */
    private void postToCourier(
            Long courierId,
            String pushSubscriptionId,
            Map<String, Object> basePayload,
            String event
    ) {
        String subscriptionId = clean(pushSubscriptionId);

        if (!subscriptionId.isBlank()) {
            Map<String, Object> subscriptionPayload = new LinkedHashMap<>(basePayload);
            subscriptionPayload.put("include_subscription_ids", List.of(subscriptionId));

            if (post(subscriptionPayload, courierId, event, "subscription_id")) {
                return;
            }

            log.warn(
                    "MotoTaksi OneSignal subscription target did not create a message; falling back to external_id. courierId={}, event={}",
                    courierId,
                    event
            );
        }

        Map<String, Object> aliasPayload = new LinkedHashMap<>(basePayload);
        aliasPayload.put(
                "include_aliases",
                Map.of("external_id", List.of(EXTERNAL_ID_PREFIX + courierId))
        );

        post(aliasPayload, courierId, event, "external_id");
    }

    private boolean post(
            Map<String, Object> payload,
            Long courierId,
            String event,
            String targetKind
    ) {
        if (appId.isBlank() || restApiKey.isBlank()) {
            log.warn("MotoTaksi OneSignal push skipped: backend app-id/rest-api-key is not configured");
            return false;
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri("/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Key " + restApiKey)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                log.warn(
                        "MotoTaksi OneSignal returned empty body. courierId={}, event={}, target={}",
                        courierId,
                        event,
                        targetKind
                );
                return false;
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String messageId = root.path("id").asText("").trim();

            if (messageId.isEmpty()) {
                log.warn(
                        "MotoTaksi OneSignal created no message. courierId={}, event={}, target={}, response={}",
                        courierId,
                        event,
                        targetKind,
                        responseBody
                );
                return false;
            }

            if (root.has("errors")) {
                log.warn(
                        "MotoTaksi OneSignal response contains target errors. courierId={}, event={}, target={}, response={}",
                        courierId,
                        event,
                        targetKind,
                        responseBody
                );
            } else {
                log.info(
                        "MotoTaksi OneSignal push accepted. courierId={}, event={}, target={}, messageId={}",
                        courierId,
                        event,
                        targetKind,
                        messageId
                );
            }

            return true;
        } catch (RestClientResponseException error) {
            log.error(
                    "MotoTaksi courier push failed. courierId={}, event={}, target={}, status={}, body={}",
                    courierId,
                    event,
                    targetKind,
                    error.getStatusCode(),
                    error.getResponseBodyAsString()
            );
            return false;
        } catch (Exception error) {
            // Dispatch state is already committed. Push failure must never roll it back;
            // foreground couriers still discover their active offer through polling.
            log.error(
                    "MotoTaksi courier push failed. courierId={}, event={}, target={}, error={}",
                    courierId,
                    event,
                    targetKind,
                    error.getMessage()
            );
            return false;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
