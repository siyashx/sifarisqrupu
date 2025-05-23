package com.codesupreme.sifarisqrupu.api.payment.controller;

import com.codesupreme.sifarisqrupu.model.payment.PaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v5")
public class PaymentController {

    private static final String PRIVATE_KEY = "VSJnqi0QMovynR5x1cSjO44H";
    private static final String PUBLIC_KEY = "i000200797";

    @PostMapping("/payment-init")
    public ResponseEntity<?> createPayment(@RequestBody PaymentRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("public_key", PUBLIC_KEY);
            dataMap.put("amount", String.format("%.2f", request.getAmount()));
            dataMap.put("currency", "AZN");
            dataMap.put("language", "az");
            dataMap.put("order_id", request.getOrderId());
            dataMap.put("description", request.getDescription());
            dataMap.put("success_redirect_url", request.getSuccessUrl());
            dataMap.put("error_redirect_url", request.getErrorUrl());

            String json = mapper.writeValueAsString(dataMap);
            String dataEncoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            String rawSignature = PRIVATE_KEY + dataEncoded + PRIVATE_KEY;

            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] sha1 = md.digest(rawSignature.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(sha1);

            Map<String, String> postBody = new HashMap<>();
            postBody.put("data", dataEncoded);
            postBody.put("signature", signature);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(postBody, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://epoint.az/api/1/request",
                    entity,
                    Map.class
            );

            if ("success".equals(response.getBody().get("status"))) {
                return ResponseEntity.ok(response.getBody().get("redirect_url"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response.getBody());
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Xəta: " + e.getMessage());
        }
    }
}
