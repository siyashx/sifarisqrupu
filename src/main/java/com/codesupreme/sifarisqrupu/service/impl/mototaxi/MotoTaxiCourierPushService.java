package com.codesupreme.sifarisqrupu.service.impl.mototaxi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class MotoTaxiCourierPushService {

    private static final Logger log = LoggerFactory.getLogger(MotoTaxiCourierPushService.class);
    private static final String EXTERNAL_ID_PREFIX = "elehber_user_";

    private final RestClient restClient;
    private final String appId;
    private final String restApiKey;
    private final String androidChannelId;
    private final int offerTtlSeconds;

    public MotoTaxiCourierPushService(
            @Value("${mototaxi.onesignal.app-id:}") String appId,
            @Value("${mototaxi.onesignal.rest-api-key:}") String restApiKey,
            @Value("${mototaxi.onesignal.android-channel-id:c668a935-ea3e-450d-afa4-5853169c36cf}") String androidChannelId,
            @Value("${mototaxi.dispatch.offer-timeout-seconds:5}") long offerTimeoutSeconds
    ) {
        this.restClient = RestClient.builder().baseUrl("https://api.onesignal.com").build();
        this.appId = appId == null ? "" : appId.trim();
        this.restApiKey = restApiKey == null ? "" : restApiKey.trim();
        this.androidChannelId = androidChannelId == null ? "" : androidChannelId.trim();
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

        Map<String, Object> payload = baseTarget(courierId, pushSubscriptionId);
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
                "alertId", orderId + "_" + System.currentTimeMillis()
        ));

        post(payload);
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

        Map<String, Object> payload = baseTarget(courierId, pushSubscriptionId);
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

        post(payload);
    }

    private Map<String, Object> baseTarget(Long courierId, String pushSubscriptionId) {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("app_id", appId);
        payload.put("target_channel", "push");

        // External ID is the stable identity. Flutter binds the active OneSignal
        // subscription to elehber_user_<id> on login/start/resume. Targeting the
        // alias avoids missing a push because the backend cached an old device
        // subscription ID after token/subscription rotation.
        payload.put(
                "include_aliases",
                Map.of("external_id", List.of(EXTERNAL_ID_PREFIX + courierId))
        );

        return payload;
    }

    private void post(Map<String, Object> payload) {
        if (appId.isBlank() || restApiKey.isBlank()) {
            log.warn("MotoTaksi OneSignal push skipped: backend app-id/rest-api-key is not configured");
            return;
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri("/notifications?c=push")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Key " + restApiKey)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            String responseBody = response.getBody();
            if (responseBody != null && responseBody.contains("\"errors\"")) {
                log.warn("MotoTaksi OneSignal response contains errors: {}", responseBody);
            } else {
                log.debug("MotoTaksi OneSignal push accepted: status={}", response.getStatusCode());
            }
        } catch (RestClientResponseException error) {
            log.error(
                    "MotoTaksi courier push failed: status={}, body={}",
                    error.getStatusCode(),
                    error.getResponseBodyAsString()
            );
        } catch (Exception error) {
            // Dispatch state is already committed. Push failure must never roll it back;
            // foreground couriers still discover the offer through polling.
            log.error("MotoTaksi courier push failed: {}", error.getMessage());
        }
    }
}
