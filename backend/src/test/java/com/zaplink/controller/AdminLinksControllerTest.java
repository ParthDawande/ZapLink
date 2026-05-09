package com.zaplink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaplink.model.Role;
import com.zaplink.model.User;
import com.zaplink.repository.LinkRepository;
import com.zaplink.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminLinksControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private LinkRepository linkRepository;

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
                {"username":"%s","email":"%s@alltest.com","password":"Password1"}
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

        String email = username + "@alltest.com";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password1"}
                                """.formatted(email).strip()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode createLink(String token, String url) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"longUrl":"%s"}
                                """.formatted(url).strip()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void softDeleteLink(String token, long linkId) throws Exception {
        mockMvc.perform(delete("/api/links/" + linkId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private void setLinkExpiry(long linkId, LocalDateTime expiresAt) {
        var link = linkRepository.findById(linkId).orElseThrow();
        link.setExpiresAt(expiresAt);
        linkRepository.save(link);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void listLinks_userRole_returns403() throws Exception {
        String userToken = registerAndGetToken("all_user_403");

        MvcResult result = mockMvc.perform(get("/api/admin/links")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("message").asText()).contains("Forbidden");
    }

    @Test
    void listLinks_statusAll_includesSoftDeletedLinks() throws Exception {
        registerAndGetToken("all_sall_admin");
        String adminToken = promoteToAdminAndLogin("all_sall_admin");

        String userToken = registerAndGetToken("all_sall_user");
        JsonNode linkJson = createLink(userToken, "https://example.com/status-all-test");
        long linkId = linkJson.get("id").asLong();
        String shortCode = linkJson.get("shortCode").asText();

        // Soft-delete the link — it should still appear under status=all
        softDeleteLink(userToken, linkId);

        MvcResult result = mockMvc.perform(get("/api/admin/links?status=all&search=" + shortCode)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("content").isArray()).isTrue();
        assertThat(json.get("content").size()).isGreaterThanOrEqualTo(1);

        // Verify the deleted link is present
        boolean found = false;
        for (JsonNode link : json.get("content")) {
            if (shortCode.equals(link.get("shortCode").asText())) {
                found = true;
                assertThat(link.get("deletedAt").isNull()).isFalse();
                assertThat(link.get("status").asText()).isEqualTo("deleted");
                break;
            }
        }
        assertThat(found).as("soft-deleted link must appear under status=all").isTrue();
    }

    @Test
    void listLinks_statusDeleted_bypassesSQLRestriction() throws Exception {
        // Critical test: @SQLRestriction("deleted_at IS NULL") must NOT prevent this query
        // from returning soft-deleted links when status=deleted is requested.
        registerAndGetToken("all_sdel_admin");
        String adminToken = promoteToAdminAndLogin("all_sdel_admin");

        String userToken = registerAndGetToken("all_sdel_user");
        JsonNode linkJson = createLink(userToken, "https://example.com/deleted-bypass-test");
        long linkId = linkJson.get("id").asLong();
        String shortCode = linkJson.get("shortCode").asText();

        softDeleteLink(userToken, linkId);

        MvcResult result = mockMvc.perform(get("/api/admin/links?status=deleted&search=" + shortCode)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = json.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThanOrEqualTo(1);

        // All returned rows must be deleted (critical assertion — proves bypass worked)
        for (JsonNode link : content) {
            assertThat(link.get("status").asText())
                    .as("status=deleted must return ONLY deleted links").isEqualTo("deleted");
            assertThat(link.get("deletedAt").isNull())
                    .as("deletedAt must be non-null for deleted links").isFalse();
        }

        // Our specific link must be present
        boolean found = false;
        for (JsonNode link : content) {
            if (shortCode.equals(link.get("shortCode").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("the soft-deleted link must appear in the result").isTrue();
    }

    @Test
    void listLinks_statusExpired_returnsOnlyExpiredLinks() throws Exception {
        registerAndGetToken("all_sexp_admin");
        String adminToken = promoteToAdminAndLogin("all_sexp_admin");

        String userToken = registerAndGetToken("all_sexp_user");
        JsonNode linkJson = createLink(userToken, "https://example.com/expired-test");
        long linkId = linkJson.get("id").asLong();
        String shortCode = linkJson.get("shortCode").asText();

        // Set expiry to the past via repository (API rejects past dates)
        setLinkExpiry(linkId, LocalDateTime.now().minusDays(1));

        MvcResult result = mockMvc.perform(get("/api/admin/links?status=expired&search=" + shortCode)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = json.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThanOrEqualTo(1);

        for (JsonNode link : content) {
            assertThat(link.get("status").asText())
                    .as("status=expired must return ONLY expired links").isEqualTo("expired");
        }

        boolean found = false;
        for (JsonNode link : content) {
            if (shortCode.equals(link.get("shortCode").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("the expired link must appear in the result").isTrue();
    }

    @Test
    void listLinks_searchByShortCode_returnsMatchingLink() throws Exception {
        registerAndGetToken("all_srch_admin");
        String adminToken = promoteToAdminAndLogin("all_srch_admin");

        String userToken = registerAndGetToken("all_srch_user");
        JsonNode linkJson = createLink(userToken, "https://example.com/search-by-code-test");
        String shortCode = linkJson.get("shortCode").asText();

        // Search with the full shortCode — should match exactly
        MvcResult result = mockMvc.perform(get("/api/admin/links?search=" + shortCode)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = json.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThanOrEqualTo(1);

        boolean found = false;
        for (JsonNode link : content) {
            if (shortCode.equals(link.get("shortCode").asText())) {
                found = true;
                // Verify the owner is populated correctly
                assertThat(link.get("owner").isNull()).isFalse();
                assertThat(link.get("owner").get("username").asText()).isEqualTo("all_srch_user");
                break;
            }
        }
        assertThat(found).as("link must be found by its shortCode").isTrue();
    }

    @Test
    void listLinks_orphanedLink_ownerIsNull() throws Exception {
        registerAndGetToken("all_orph_admin");
        String adminToken = promoteToAdminAndLogin("all_orph_admin");

        // Create user whose account we will hard-delete after soft-deleting their link
        String userToken = registerAndGetToken("all_orph_user");
        JsonNode linkJson = createLink(userToken, "https://example.com/orphan-test");
        long linkId = linkJson.get("id").asLong();
        long userId = createdUserIds.get(createdUserIds.size() - 1);
        String shortCode = linkJson.get("shortCode").asText();

        // Soft-delete the link, then hard-delete the user (simulates Endpoint 13 cascade)
        softDeleteLink(userToken, linkId);
        userRepository.deleteById(userId);
        createdUserIds.remove(userId); // already deleted, skip in @AfterEach

        MvcResult result = mockMvc.perform(get("/api/admin/links?status=deleted&search=" + shortCode)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = json.get("content");
        assertThat(content.isArray()).isTrue();

        boolean found = false;
        for (JsonNode link : content) {
            if (shortCode.equals(link.get("shortCode").asText())) {
                found = true;
                // Owner must be null — user was deleted, LEFT JOIN returns no row
                assertThat(link.get("owner").isNull())
                        .as("owner must be null for orphaned links").isTrue();
                break;
            }
        }
        assertThat(found).as("orphaned link must appear in deleted results").isTrue();
    }

    @Test
    void listLinks_invalidStatus_returns400() throws Exception {
        registerAndGetToken("all_invst_admin");
        String adminToken = promoteToAdminAndLogin("all_invst_admin");

        MvcResult result = mockMvc.perform(get("/api/admin/links?status=unknown")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asText()).contains("Invalid status");
    }

    @Test
    void listLinks_responseShape_hasAllRequiredFields() throws Exception {
        registerAndGetToken("all_shp_admin");
        String adminToken = promoteToAdminAndLogin("all_shp_admin");

        String userToken = registerAndGetToken("all_shp_user");
        JsonNode linkJson = createLink(userToken, "https://example.com/shape-test");
        String shortCode = linkJson.get("shortCode").asText();

        MvcResult result = mockMvc.perform(get("/api/admin/links?search=" + shortCode)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        // Paginated envelope fields
        assertThat(json.has("content")).isTrue();
        assertThat(json.has("totalElements")).isTrue();
        assertThat(json.has("totalPages")).isTrue();

        JsonNode first = json.get("content").get(0);
        assertThat(first.has("id")).isTrue();
        assertThat(first.has("shortCode")).isTrue();
        assertThat(first.has("shortUrl")).isTrue();
        assertThat(first.has("longUrl")).isTrue();
        assertThat(first.has("expiresAt")).isTrue();
        assertThat(first.has("isActive")).isTrue();
        assertThat(first.has("disabledReason")).isTrue();
        assertThat(first.has("deletedAt")).isTrue();
        assertThat(first.has("status")).isTrue();
        assertThat(first.has("clickCount")).isTrue();
        assertThat(first.has("owner")).isTrue();
        assertThat(first.has("createdAt")).isTrue();
    }
}
