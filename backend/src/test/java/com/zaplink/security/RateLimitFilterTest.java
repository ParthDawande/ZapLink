package com.zaplink.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaplink.model.Role;
import com.zaplink.model.User;
import com.zaplink.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Per-endpoint rate limiting integration tests.
 *
 * Strategy: capacity=3, refill-period=1s for every policy so tests run fast.
 * Users are created directly via UserRepository + JwtUtil (not via the register
 * endpoint) so setup calls don't consume rate-limit tokens.
 * Each test uses a distinct bucket key (userId or IP) to prevent cross-test state.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "ratelimit.create-link.capacity=3",
    "ratelimit.create-link.refill-period-seconds=1",
    "ratelimit.register.capacity=3",
    "ratelimit.register.refill-period-seconds=1",
    "ratelimit.login.capacity=3",
    "ratelimit.login.refill-period-seconds=1",
    "ratelimit.default.capacity=3",
    "ratelimit.default.refill-period-seconds=1",
})
class RateLimitFilterTest {

    // Distinct IPs per test scenario — no cross-contamination between bucket keys.
    private static final String REGISTER_TEST_IP = "10.60.0.1";
    private static final String LOGIN_TEST_IP    = "10.70.0.1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(id -> {
            try { userRepository.deleteById(id); } catch (Exception ignored) {}
        });
        createdUserIds.clear();
    }

    // Creates a user directly in the DB and mints a JWT.
    // Bypasses the register endpoint so no register-rate-limit tokens are consumed.
    private String createUserAndGetToken(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@rl.example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setRole(Role.USER);
        user.setIsActive(true);
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return jwtUtil.generateToken(saved.getId(), saved.getUsername(), saved.getRole());
    }

    private void assert429Envelope(String responseBody) throws Exception {
        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.get("status").asInt()).isEqualTo(429);
        assertThat(json.get("error").asText()).isNotBlank();
        assertThat(json.get("message").asText()).isEqualTo("Too many requests. Please try again later.");
        assertThat(json.get("path").asText()).isNotBlank();
    }

    // ── create-link (POST /api/links, keyed by userId) ────────────────────────

    @Test
    void createLink_3requestsSucceed_4thReturns429() throws Exception {
        String token = createUserAndGetToken("rl_cl_exhaust");
        String body = """
                {"longUrl":"https://ratelimit-test.example.com/exhaust"}
                """.strip();

        // First 3 within quota — may return 201 (new) or 200 (dedup), both are 2xx.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/links")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is2xxSuccessful());
        }

        // 4th request exceeds the bucket.
        MvcResult result = mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assert429Envelope(result.getResponse().getContentAsString());
    }

    @Test
    void createLink_recoversAfterRefillPeriod() throws Exception {
        // refill-period-seconds=1 lets us test recovery without a long sleep.
        String token = createUserAndGetToken("rl_cl_recover");
        String body = """
                {"longUrl":"https://ratelimit-test.example.com/recover"}
                """.strip();

        // Exhaust the bucket (3 requests).
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/links")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
        }
        // Confirm bucket is exhausted.
        mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());

        // Wait for the 1-second interval to elapse and the bucket to refill.
        Thread.sleep(1200);

        // Should succeed after refill.
        mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful());
    }

    // ── login (POST /api/auth/login, keyed by IP) ─────────────────────────────

    @Test
    void login_3attemptsSucceed_4thReturns429() throws Exception {
        // Invalid credentials return 401 — rate limit passes, auth check fails.
        // After 3 such attempts from the same IP, the 4th is blocked by rate limiting.
        String invalidBody = """
                {"email":"nobody@nowhere.example.com","password":"WrongPass1"}
                """.strip();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> { req.setRemoteAddr(LOGIN_TEST_IP); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isUnauthorized());
        }

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr(LOGIN_TEST_IP); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assert429Envelope(result.getResponse().getContentAsString());
    }

    // ── register (POST /api/auth/register, keyed by IP) ──────────────────────

    @Test
    void register_ipExceedsLimit_returns429() throws Exception {
        // Send 3 register requests with an invalid body (missing username).
        // Rate limit is checked before Bean Validation, so tokens are consumed
        // even though the requests return 400 (no users are created).
        String badBody = """
                {"email":"bad@rl.example.com","password":"short"}
                """.strip();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/register")
                            .with(req -> { req.setRemoteAddr(REGISTER_TEST_IP); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badBody))
                    .andExpect(status().isBadRequest());
        }

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .with(req -> { req.setRemoteAddr(REGISTER_TEST_IP); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assert429Envelope(result.getResponse().getContentAsString());
    }
}
