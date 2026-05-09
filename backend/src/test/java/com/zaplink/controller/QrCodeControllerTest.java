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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QrCodeControllerTest {

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

    private String registerAndGetToken(String username) throws Exception {
        String body = """
                {"username":"%s","email":"%s@qrtest.com","password":"Password1"}
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

    private long createLink(String token, String url) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"longUrl":"%s"}
                                """.formatted(url).strip()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void assertJsonEnvelope(String responseBody, int expectedStatus) throws Exception {
        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.has("timestamp")).as("envelope must have 'timestamp'").isTrue();
        assertThat(json.has("status")).as("envelope must have 'status'").isTrue();
        assertThat(json.has("error")).as("envelope must have 'error'").isTrue();
        assertThat(json.has("message")).as("envelope must have 'message'").isTrue();
        assertThat(json.has("path")).as("envelope must have 'path'").isTrue();
        assertThat(json.get("status").asInt()).isEqualTo(expectedStatus);
    }

    @Test
    void getQr_validOwnLink_returns200PngWithSignature() throws Exception {
        String token = registerAndGetToken("qrctl_valid");
        long linkId = createLink(token, "https://example.com/qr-controller-test");

        MvcResult result = mockMvc.perform(get("/api/links/" + linkId + "/qr")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0] & 0xFF).isEqualTo(0x89);
        assertThat(bytes[1] & 0xFF).isEqualTo(0x50); // 'P'
        assertThat(bytes[2] & 0xFF).isEqualTo(0x4E); // 'N'
        assertThat(bytes[3] & 0xFF).isEqualTo(0x47); // 'G'
    }

    // Key regression: produces=image/png must NOT prevent error responses from returning JSON.
    @Test
    void getQr_nonExistentId_returns404JsonEnvelope() throws Exception {
        String token = registerAndGetToken("qrctl_notfound");

        MvcResult result = mockMvc.perform(get("/api/links/99999999/qr")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertJsonEnvelope(result.getResponse().getContentAsString(), 404);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).isEqualTo("Link not found");
    }

    @Test
    void getQr_noToken_returns401JsonEnvelope() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/links/1/qr"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertJsonEnvelope(result.getResponse().getContentAsString(), 401);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).isEqualTo("Authentication required");
    }
}
