package com.codesupreme.sifarisqrupu.service.impl.superadmin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class WhatsappBridgeRuntimeRefreshNotifier {

    private final HttpClient httpClient;
    private final String refreshUrl;

    public WhatsappBridgeRuntimeRefreshNotifier(
            @Value("${whatsapp.bridge.runtime-refresh-url:http://127.0.0.1:4242/internal/runtime-refresh}")
            String refreshUrl
    ) {
        this.refreshUrl = refreshUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(700))
                .build();
    }

    /*
     * Admin əməliyyatı DB-də artıq commit olduqdan sonra çağırılır.
     * Bridge əlçatmazdırsa admin əməliyyatı uğursuz sayılmır:
     * mövcud periodik runtime polling fallback kimi qalır.
     */
    public void refreshNowBestEffort() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(refreshUrl))
                    .timeout(Duration.ofMillis(1500))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // Polling fallback runtime config-i növbəti intervalda götürəcək.
        }
    }
}
