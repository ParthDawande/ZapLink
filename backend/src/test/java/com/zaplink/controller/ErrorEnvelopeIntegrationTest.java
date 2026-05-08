package com.zaplink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaplink.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ErrorEnvelopeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(id -> {
            try { userRepository.deleteById(id); } catch (Exception ignored) {}
        });
        createdUserIds.clear();
    }

    // Registers a fresh user, records their ID for cleanup, and returns the JWT.
    private String registerAndGetToken(String username) throws Exception {
        String body = """
                {"username":"%s","email":"%s@example.com","password":"Password1"}
                """.formatted(username, username).strip();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        createdUserIds.add(json.get("userId").asLong());
        return json.get("token").asText();
    }

    // Asserts all five envelope fields are present and non-blank, and that status matches.
    private void assertEnvelope(String responseBody, int expectedStatus) throws Exception {
        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.has("timestamp")).as("envelope must have 'timestamp'").isTrue();
        assertThat(json.has("status")).as("envelope must have 'status'").isTrue();
        assertThat(json.has("error")).as("envelope must have 'error'").isTrue();
        assertThat(json.has("message")).as("envelope must have 'message'").isTrue();
        assertThat(json.has("path")).as("envelope must have 'path'").isTrue();

        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.get("status").asInt()).isEqualTo(expectedStatus);
        assertThat(json.get("error").asText()).isNotBlank();
        assertThat(json.get("message").asText()).isNotBlank();
        assertThat(json.get("path").asText()).isNotBlank();
    }

    @Test
    void register_badBody_returns400Envelope() throws Exception {
        // Only "username":"a" — too short, plus email and password absent
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"a"}
                                """.strip()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEnvelope(result.getResponse().getContentAsString(), 400);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        // Bean Validation surfaces a message about the failing field — must not be blank
        assertThat(json.get("message").asText()).isNotBlank();
    }

    @Test
    void createLink_noToken_returns401Envelope() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com"}
                                """.strip()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEnvelope(result.getResponse().getContentAsString(), 401);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).isEqualTo("Authentication required");
    }

    @Test
    void createLink_malformedJson_returns400Envelope() throws Exception {
        String token = registerAndGetToken("ee_malformed");

        MvcResult result = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{not valid}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEnvelope(result.getResponse().getContentAsString(), 400);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).isEqualTo("Malformed JSON request body");
    }

    @Test
    void getLink_validToken_nonExistentId_returns404Envelope() throws Exception {
        String token = registerAndGetToken("ee_notfound");

        MvcResult result = mockMvc.perform(get("/api/links/99999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEnvelope(result.getResponse().getContentAsString(), 404);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).isEqualTo("Link not found");
    }
}
