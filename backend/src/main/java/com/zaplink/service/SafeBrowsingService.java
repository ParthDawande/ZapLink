package com.zaplink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class SafeBrowsingService {

    private static final Logger log = LoggerFactory.getLogger(SafeBrowsingService.class);

    private final WebClient webClient;

    @Value("${safebrowsing.api-key:}")
    private String apiKey;

    @Value("${safebrowsing.timeout-ms:2000}")
    private long timeoutMs;

    @Value("${safebrowsing.enabled:true}")
    private boolean enabled;

    // Overrideable in tests via ReflectionTestUtils without replacing the WebClient.
    @Value("${safebrowsing.api-endpoint:https://safebrowsing.googleapis.com/v4/threatMatches:find}")
    private String apiEndpoint;

    public SafeBrowsingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Returns true if the URL is safe (or if Safe Browsing is disabled / the check fails).
     * Fail-open: any exception (timeout, I/O error, unexpected response) returns true.
     */
    public boolean isSafe(String url) {
        if (!enabled) {
            return true;
        }
        try {
            Map<String, Object> body = Map.of(
                    "client", Map.of("clientId", "zaplink", "clientVersion", "1.0"),
                    "threatInfo", Map.of(
                            "threatTypes", List.of(
                                    "MALWARE", "SOCIAL_ENGINEERING",
                                    "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                            "platformTypes", List.of("ANY_PLATFORM"),
                            "threatEntryTypes", List.of("URL"),
                            "threatEntries", List.of(Map.of("url", url))
                    )
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri(apiEndpoint + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (response == null) {
                return true;
            }
            List<?> matches = (List<?>) response.get("matches");
            return matches == null || matches.isEmpty();

        } catch (Exception e) {
            log.warn("Safe Browsing check failed for url={}: {}", url, e.getMessage());
            return true;
        }
    }
}
