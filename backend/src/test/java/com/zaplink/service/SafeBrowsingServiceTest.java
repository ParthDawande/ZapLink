package com.zaplink.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SafeBrowsingService using MockWebServer.
 *
 * Uses HttpClient.newConnection() (no pooling) so stale Reactor Netty connections
 * from previous tests cannot consume MockWebServer responses if the OS re-uses the
 * same port for a newly started MockWebServer instance.
 */
class SafeBrowsingServiceTest {

    private MockWebServer mockServer;
    private SafeBrowsingService service;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        // No-pool connector: each call opens a fresh TCP connection, eliminating any
        // cross-test state from the global Reactor Netty connection pool.
        HttpClient httpClient = HttpClient.newConnection();
        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        service = new SafeBrowsingService(builder);
        ReflectionTestUtils.setField(service, "apiEndpoint",
                mockServer.url("/").toString());
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "timeoutMs", 500L);
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void isSafe_emptyResponseBody_returnsTrue() {
        mockServer.enqueue(new MockResponse()
                .setBody("{}")
                .addHeader("Content-Type", "application/json"));

        assertThat(service.isSafe("https://example.com")).isTrue();
    }

    @Test
    void isSafe_matchesPresent_returnsFalse() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"matches\":[{\"threatType\":\"MALWARE\",\"platformType\":\"ANY_PLATFORM\"}]}")
                .addHeader("Content-Type", "application/json"));

        assertThat(service.isSafe("http://malware.testing.google.test/testing/malware/")).isFalse();
    }

    @Test
    void isSafe_serverError_failOpenReturnsTrue() {
        mockServer.enqueue(new MockResponse().setResponseCode(500));

        assertThat(service.isSafe("https://example.com")).isTrue();
    }

    @Test
    void isSafe_responseExceedsTimeout_failOpenReturnsTrue() {
        // 2 s body delay exceeds the 500 ms timeout set in setUp → TimeoutException → fail-open.
        mockServer.enqueue(new MockResponse()
                .setBody("{\"matches\":[{\"threatType\":\"MALWARE\"}]}")
                .addHeader("Content-Type", "application/json")
                .setBodyDelay(2_000, TimeUnit.MILLISECONDS));

        assertThat(service.isSafe("https://example.com")).isTrue();
    }

    @Test
    void isSafe_disabledByConfig_returnsTrueWithoutCallingApi() {
        ReflectionTestUtils.setField(service, "enabled", false);
        // MockWebServer has no enqueued response — any real HTTP call would fail.
        assertThat(service.isSafe("http://malware.testing.google.test/testing/malware/")).isTrue();
    }
}
