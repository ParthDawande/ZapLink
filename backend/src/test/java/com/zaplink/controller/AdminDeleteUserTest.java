package com.zaplink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaplink.model.Click;
import com.zaplink.model.DisabledReason;
import com.zaplink.model.Role;
import com.zaplink.model.User;
import com.zaplink.repository.ClickRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDeleteUserTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private LinkRepository linkRepository;
    @Autowired private ClickRepository clickRepository;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(id -> {
            try { userRepository.deleteById(id); } catch (Exception ignored) {}
        });
        createdUserIds.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record RegisteredUser(long userId, String token) {}

    private RegisteredUser register(String username) throws Exception {
        String body = """
                {"username":"%s","email":"%s@delusr.com","password":"Password1"}
                """.formatted(username, username).strip();
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        long userId = json.get("userId").asLong();
        createdUserIds.add(userId);
        return new RegisteredUser(userId, json.get("token").asText());
    }

    private String promoteToAdminAndLogin(String username) throws Exception {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@delusr.com","password":"Password1"}
                                """.formatted(username).strip()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
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

    private void banUser(String adminToken, long userId) throws Exception {
        mockMvc.perform(patch("/api/admin/users/" + userId + "/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"banned\":true}"))
                .andExpect(status().isOk());
    }

    private void adminDisableLink(String adminToken, long linkId) throws Exception {
        mockMvc.perform(patch("/api/admin/links/" + linkId + "/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"disabled\":true}"))
                .andExpect(status().isOk());
    }

    private Click insertClick(long linkId) {
        Click click = new Click();
        click.setLinkId(linkId);
        click.setClickedAt(LocalDateTime.now());
        return clickRepository.save(click);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void deleteUser_unbannedUser_returns400() throws Exception {
        RegisteredUser admin = register("del_unban_admin");
        String adminToken = promoteToAdminAndLogin("del_unban_admin");
        RegisteredUser user = register("del_unban_user");

        MvcResult result = mockMvc.perform(delete("/api/admin/users/" + user.userId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asText()).containsIgnoringCase("banned");

        // User must still exist
        assertThat(userRepository.findById(user.userId())).isPresent();
    }

    @Test
    void deleteUser_self_returns400() throws Exception {
        RegisteredUser admin = register("del_self_admin");
        String adminToken = promoteToAdminAndLogin("del_self_admin");

        MvcResult result = mockMvc.perform(delete("/api/admin/users/" + admin.userId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asText()).containsIgnoringCase("yourself");

        // Admin must still exist
        assertThat(userRepository.findById(admin.userId())).isPresent();
    }

    @Test
    void deleteUser_nonExistent_returns404() throws Exception {
        RegisteredUser admin = register("del_404_admin");
        String adminToken = promoteToAdminAndLogin("del_404_admin");

        MvcResult result = mockMvc.perform(delete("/api/admin/users/999999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(404);
    }

    @Test
    void deleteUser_bannedUser_returns204AndUserIsGone() throws Exception {
        RegisteredUser admin = register("del_ok_admin");
        String adminToken = promoteToAdminAndLogin("del_ok_admin");
        RegisteredUser user = register("del_ok_user");

        banUser(adminToken, user.userId());

        mockMvc.perform(delete("/api/admin/users/" + user.userId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // User must be gone from the DB
        assertThat(userRepository.findById(user.userId())).isEmpty();

        // createdUserIds still has user's id; @AfterEach handles the already-deleted case silently
    }

    @Test
    void deleteUser_linksAreSoftDeletedAndOrphaned_clickHistoryPreserved() throws Exception {
        RegisteredUser admin = register("del_lnk_admin");
        String adminToken = promoteToAdminAndLogin("del_lnk_admin");
        RegisteredUser user = register("del_lnk_user");

        // Create two links — L1 active, L2 admin-disabled
        long l1Id = createLink(user.token(), "https://example.com/del-lnk-l1");
        long l2Id = createLink(user.token(), "https://example.com/del-lnk-l2");
        adminDisableLink(adminToken, l2Id);

        // Insert 3 clicks for L1 directly (simulates redirect traffic)
        insertClick(l1Id);
        insertClick(l1Id);
        insertClick(l1Id);

        // Ban then delete
        banUser(adminToken, user.userId());
        mockMvc.perform(delete("/api/admin/users/" + user.userId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // User is gone
        assertThat(userRepository.findById(user.userId())).isEmpty();

        // L1: soft-deleted (user_id FK cascade to NULL is a production-DB concern;
        // in H2 test env, Hibernate create-drop omits ON DELETE SET NULL since Link.userId
        // is a bare @Column with no JPA FK annotation — verified via the admin API instead).
        var l1 = linkRepository.findByIdIncludingDeleted(l1Id).orElseThrow();
        assertThat(l1.getDeletedAt()).as("L1 must be soft-deleted").isNotNull();

        // L2: also soft-deleted; disabled_reason must be intact
        var l2 = linkRepository.findByIdIncludingDeleted(l2Id).orElseThrow();
        assertThat(l2.getDeletedAt()).as("L2 must be soft-deleted").isNotNull();
        assertThat(l2.getDisabledReason()).isEqualTo(DisabledReason.ADMIN_DISABLED);

        // Click history for L1 must still exist — 3 clicks preserved
        assertThat(clickRepository.countByLinkId(l1Id))
                .as("Click history must be preserved after user deletion").isEqualTo(3L);
    }

    @Test
    void deleteUser_orphanedLinks_appearInAdminLinksDeleted_withNullOwner() throws Exception {
        RegisteredUser admin = register("del_orph_admin");
        String adminToken = promoteToAdminAndLogin("del_orph_admin");
        RegisteredUser user = register("del_orph_user");

        long linkId = createLink(user.token(), "https://example.com/del-orph-link");

        // Get the shortCode before deletion
        MvcResult linkResult = mockMvc.perform(get("/api/admin/links?search=del-orph-link")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode linkPage = objectMapper.readTree(linkResult.getResponse().getContentAsString());
        String shortCode = linkPage.get("content").get(0).get("shortCode").asText();

        banUser(adminToken, user.userId());
        mockMvc.perform(delete("/api/admin/users/" + user.userId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Verify orphaned link appears under status=deleted with owner=null
        MvcResult deletedResult = mockMvc.perform(
                        get("/api/admin/links?status=deleted&search=" + shortCode)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode deletedPage = objectMapper.readTree(deletedResult.getResponse().getContentAsString());
        JsonNode content = deletedPage.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThanOrEqualTo(1);

        JsonNode orphanedLink = null;
        for (JsonNode link : content) {
            if (shortCode.equals(link.get("shortCode").asText())) {
                orphanedLink = link;
                break;
            }
        }
        assertThat(orphanedLink).as("orphaned link must appear in status=deleted results").isNotNull();
        assertThat(orphanedLink.get("status").asText()).isEqualTo("deleted");
        assertThat(orphanedLink.get("owner").isNull())
                .as("owner must be null after user hard-delete").isTrue();
    }

    @Test
    void deleteUser_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void deleteUser_userRole_returns403() throws Exception {
        RegisteredUser user = register("del_usr403_user");

        mockMvc.perform(delete("/api/admin/users/1")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
