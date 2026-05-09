package com.zaplink.controller;

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
class AdminControllerTest {

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String registerAndGetToken(String username) throws Exception {
        String body = """
                {"username":"%s","email":"%s@admtest.com","password":"Password1"}
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

    private String promoteToAdminAndLogin(String username) throws Exception {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        // LoginRequest requires email, not username.
        String email = username + "@admtest.com";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password1"}
                                """.formatted(email).strip()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private void disableUser(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setIsActive(false);
        userRepository.save(user);
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

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void listUsers_noToken_returns401JsonEnvelope() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertJsonEnvelope(result.getResponse().getContentAsString(), 401);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).isEqualTo("Authentication required");
    }

    @Test
    void listUsers_userRole_returns403JsonEnvelope() throws Exception {
        String userToken = registerAndGetToken("adm_usr_role");

        MvcResult result = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertJsonEnvelope(result.getResponse().getContentAsString(), 403);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).contains("Forbidden");
    }

    @Test
    void listUsers_adminRole_returns200WithUserList() throws Exception {
        registerAndGetToken("adm_200_usr");
        String adminToken = promoteToAdminAndLogin("adm_200_usr");

        MvcResult result = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isGreaterThanOrEqualTo(1);

        // Find our admin user in the list and assert field shape
        JsonNode adminUser = null;
        for (JsonNode node : json) {
            if ("adm_200_usr".equals(node.get("username").asText())) {
                adminUser = node;
                break;
            }
        }
        assertThat(adminUser).as("admin user must appear in results").isNotNull();
        assertThat(adminUser.get("role").asText()).isEqualTo("ADMIN");
        assertThat(adminUser.has("id")).isTrue();
        assertThat(adminUser.has("email")).isTrue();
        assertThat(adminUser.has("isActive")).isTrue();
        assertThat(adminUser.has("linkCount")).isTrue();
        assertThat(adminUser.has("createdAt")).isTrue();
        // password must never leak
        assertThat(adminUser.has("passwordHash")).isFalse();
    }

    @Test
    void listUsers_withSearch_returnsOnlyMatchingUsers() throws Exception {
        registerAndGetToken("adm_srch_admin");
        String adminToken = promoteToAdminAndLogin("adm_srch_admin");
        registerAndGetToken("adm_srch_target");

        MvcResult result = mockMvc.perform(get("/api/admin/users?search=adm_srch_target")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode node : json) {
            String username = node.get("username").asText();
            String email = node.get("email").asText();
            assertThat(username.toLowerCase() + email.toLowerCase())
                    .as("every result must match the search term")
                    .contains("adm_srch_target");
        }
    }

    @Test
    void listUsers_statusActive_excludesDisabledUsers() throws Exception {
        registerAndGetToken("adm_act_admin");
        String adminToken = promoteToAdminAndLogin("adm_act_admin");
        registerAndGetToken("adm_act_dsbl");
        disableUser("adm_act_dsbl");

        // Search + status to keep scope narrow
        MvcResult result = mockMvc.perform(get("/api/admin/users?search=adm_act_&status=active")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
        for (JsonNode node : json) {
            assertThat(node.get("isActive").asBoolean())
                    .as("status=active must return only active users").isTrue();
        }
        // Disabled user must not appear
        boolean foundDisabled = false;
        for (JsonNode node : json) {
            if ("adm_act_dsbl".equals(node.get("username").asText())) {
                foundDisabled = true;
                break;
            }
        }
        assertThat(foundDisabled).as("disabled user must not appear with status=active").isFalse();
    }

    @Test
    void listUsers_statusDisabled_returnsOnlyDisabledUsers() throws Exception {
        registerAndGetToken("adm_dsbl_admin");
        String adminToken = promoteToAdminAndLogin("adm_dsbl_admin");
        registerAndGetToken("adm_dsbl_tgt");
        disableUser("adm_dsbl_tgt");

        MvcResult result = mockMvc.perform(get("/api/admin/users?search=adm_dsbl_&status=disabled")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode node : json) {
            assertThat(node.get("isActive").asBoolean())
                    .as("status=disabled must return only disabled users").isFalse();
        }
    }

    @Test
    void listUsers_invalidStatus_returns400() throws Exception {
        registerAndGetToken("adm_invst_usr");
        String adminToken = promoteToAdminAndLogin("adm_invst_usr");

        MvcResult result = mockMvc.perform(get("/api/admin/users?status=banned")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertJsonEnvelope(result.getResponse().getContentAsString(), 400);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).contains("Invalid status");
    }

    @Test
    void listUsers_linkCountReflectsCreatedLinks() throws Exception {
        registerAndGetToken("adm_lnk_admin");
        String adminToken = promoteToAdminAndLogin("adm_lnk_admin");

        String userToken = registerAndGetToken("adm_lnk_tgt");
        createLink(userToken, "https://example.com/link-count-test-1");
        createLink(userToken, "https://example.com/link-count-test-2");

        MvcResult result = mockMvc.perform(get("/api/admin/users?search=adm_lnk_tgt")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();

        JsonNode targetUser = null;
        for (JsonNode node : json) {
            if ("adm_lnk_tgt".equals(node.get("username").asText())) {
                targetUser = node;
                break;
            }
        }
        assertThat(targetUser).as("target user must appear in search results").isNotNull();
        assertThat(targetUser.get("linkCount").asLong())
                .as("linkCount must equal the number of links created").isEqualTo(2L);
    }
}
