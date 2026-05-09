package com.zaplink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaplink.model.DisabledReason;
import com.zaplink.model.Link;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminBanCascadeTest {

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

    private record RegisteredUser(long userId, String token) {}

    private RegisteredUser register(String username, String domain) throws Exception {
        String body = """
                {"username":"%s","email":"%s@%s","password":"Password1"}
                """.formatted(username, username, domain).strip();
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

    private String promoteToAdminAndLogin(String username, String domain) throws Exception {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@%s","password":"Password1"}
                                """.formatted(username, domain).strip()))
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

    private MvcResult patchBan(String adminToken, long userId, boolean banned) throws Exception {
        return mockMvc.perform(patch("/api/admin/users/" + userId + "/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"banned":%s}
                                """.formatted(banned).strip()))
                .andReturn();
    }

    private void adminDisableLink(String adminToken, long linkId) throws Exception {
        mockMvc.perform(patch("/api/admin/links/" + linkId + "/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"disabled\":true}"))
                .andExpect(status().isOk());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void ban_cascadesOnlyActiveLinks_leavesAdminDisabledAndDeletedUntouched() throws Exception {
        // Setup: admin + user with 3 links in different states
        RegisteredUser admin = register("ban_cas_admin", "bantest.com");
        String adminToken = promoteToAdminAndLogin("ban_cas_admin", "bantest.com");

        RegisteredUser user = register("ban_cas_user", "bantest.com");
        long l1 = createLink(user.token(), "https://example.com/ban-l1");
        long l2 = createLink(user.token(), "https://example.com/ban-l2");
        long l3 = createLink(user.token(), "https://example.com/ban-l3");

        // L2: admin-disable it
        adminDisableLink(adminToken, l2);

        // L3: soft-delete directly via repo
        Link link3 = linkRepository.findById(l3).orElseThrow();
        link3.setDeletedAt(LocalDateTime.now());
        linkRepository.save(link3);

        // Ban the user
        MvcResult banResult = patchBan(adminToken, user.userId(), true);
        assertThat(banResult.getResponse().getStatus()).isEqualTo(200);

        JsonNode response = objectMapper.readTree(banResult.getResponse().getContentAsString());
        assertThat(response.get("isActive").asBoolean()).isFalse();

        // L1: must be USER_BANNED
        Link l1state = linkRepository.findById(l1).orElseThrow();
        assertThat(l1state.getIsActive()).isFalse();
        assertThat(l1state.getDisabledReason()).isEqualTo(DisabledReason.USER_BANNED);

        // L2: must stay ADMIN_DISABLED — the critical assertion
        Link l2state = linkRepository.findById(l2).orElseThrow();
        assertThat(l2state.getIsActive()).isFalse();
        assertThat(l2state.getDisabledReason())
                .as("ADMIN_DISABLED link must NOT be overwritten by ban cascade")
                .isEqualTo(DisabledReason.ADMIN_DISABLED);

        // L3: soft-deleted, @SQLRestriction keeps it out of cascade — state unchanged
        Link l3state = linkRepository.findByIdIncludingDeleted(l3).orElseThrow();
        assertThat(l3state.getDeletedAt()).isNotNull();
    }

    @Test
    void unban_restoresOnlyUserBannedLinks_leavesAdminDisabledUntouched() throws Exception {
        // Setup identical to the ban test — ban then unban
        RegisteredUser admin = register("unban_cas_admin", "bantest.com");
        String adminToken = promoteToAdminAndLogin("unban_cas_admin", "bantest.com");

        RegisteredUser user = register("unban_cas_user", "bantest.com");
        long l1 = createLink(user.token(), "https://example.com/unban-l1");
        long l2 = createLink(user.token(), "https://example.com/unban-l2");

        // L2: admin-disable before banning
        adminDisableLink(adminToken, l2);

        // Ban
        patchBan(adminToken, user.userId(), true);

        // Unban
        MvcResult unbanResult = patchBan(adminToken, user.userId(), false);
        assertThat(unbanResult.getResponse().getStatus()).isEqualTo(200);

        JsonNode response = objectMapper.readTree(unbanResult.getResponse().getContentAsString());
        assertThat(response.get("isActive").asBoolean()).isTrue();

        // L1: must be restored to active with no disabledReason
        Link l1state = linkRepository.findById(l1).orElseThrow();
        assertThat(l1state.getIsActive()).isTrue();
        assertThat(l1state.getDisabledReason()).isNull();

        // L2: must stay ADMIN_DISABLED through the full ban→unban cycle — critical assertion
        Link l2state = linkRepository.findById(l2).orElseThrow();
        assertThat(l2state.getIsActive())
                .as("ADMIN_DISABLED link must stay inactive after unban")
                .isFalse();
        assertThat(l2state.getDisabledReason())
                .as("ADMIN_DISABLED link must retain its disabled reason after unban")
                .isEqualTo(DisabledReason.ADMIN_DISABLED);
    }

    @Test
    void ban_selfBan_returns400() throws Exception {
        RegisteredUser admin = register("ban_self_admin", "bantest.com");
        String adminToken = promoteToAdminAndLogin("ban_self_admin", "bantest.com");

        MvcResult result = patchBan(adminToken, admin.userId(), true);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).containsIgnoringCase("cannot ban or unban yourself");
    }

    @Test
    void unban_selfUnban_returns400() throws Exception {
        RegisteredUser admin = register("unban_self_admin", "bantest.com");
        String adminToken = promoteToAdminAndLogin("unban_self_admin", "bantest.com");

        MvcResult result = patchBan(adminToken, admin.userId(), false);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("message").asText()).containsIgnoringCase("cannot ban or unban yourself");
    }

    @Test
    void ban_adminBansAnotherAdmin_succeeds() throws Exception {
        RegisteredUser admin1 = register("ban_aa_admin1", "bantest.com");
        String admin1Token = promoteToAdminAndLogin("ban_aa_admin1", "bantest.com");

        RegisteredUser admin2 = register("ban_aa_admin2", "bantest.com");
        promoteToAdminAndLogin("ban_aa_admin2", "bantest.com");

        MvcResult result = patchBan(admin1Token, admin2.userId(), true);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("isActive").asBoolean()).isFalse();

        // Restore for cleanup
        patchBan(admin1Token, admin2.userId(), false);
    }

    @Test
    void ban_nonExistentUser_returns404() throws Exception {
        RegisteredUser admin = register("ban_404_admin", "bantest.com");
        String adminToken = promoteToAdminAndLogin("ban_404_admin", "bantest.com");

        MvcResult result = patchBan(adminToken, 999999999L, true);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(404);
    }

    @Test
    void ban_alreadyBannedUser_isIdempotent() throws Exception {
        RegisteredUser admin = register("ban_idem_admin", "bantest.com");
        String adminToken = promoteToAdminAndLogin("ban_idem_admin", "bantest.com");
        RegisteredUser user = register("ban_idem_user", "bantest.com");

        // First ban
        MvcResult first = patchBan(adminToken, user.userId(), true);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        // Second ban — must still be 200, no error
        MvcResult second = patchBan(adminToken, user.userId(), true);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(json.get("isActive").asBoolean()).isFalse();
    }

    @Test
    void unban_alreadyActiveUser_isIdempotent() throws Exception {
        RegisteredUser admin = register("unban_idem_admin", "bantest.com");
        String adminToken = promoteToAdminAndLogin("unban_idem_admin", "bantest.com");
        RegisteredUser user = register("unban_idem_user", "bantest.com");

        // User is active — unban should be a no-op, 200
        MvcResult result = patchBan(adminToken, user.userId(), false);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("isActive").asBoolean()).isTrue();
    }

    @Test
    void ban_blocksLogin_unban_restoresLogin() throws Exception {
        RegisteredUser admin = register("ban_login_admin", "bantest.com");
        String adminToken = promoteToAdminAndLogin("ban_login_admin", "bantest.com");
        RegisteredUser user = register("ban_login_user", "bantest.com");

        // Ban user
        MvcResult banResult = patchBan(adminToken, user.userId(), true);
        assertThat(banResult.getResponse().getStatus())
                .as("ban must succeed before testing login block").isEqualTo(200);

        // Banned user cannot log in
        MvcResult loginBanned = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ban_login_user@bantest.com\",\"password\":\"Password1\"}"))
                .andReturn();
        assertThat(loginBanned.getResponse().getStatus()).isEqualTo(403);

        // Unban user
        patchBan(adminToken, user.userId(), false);

        // Unbanned user can log in again
        MvcResult loginRestored = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ban_login_user@bantest.com\",\"password\":\"Password1\"}"))
                .andReturn();
        assertThat(loginRestored.getResponse().getStatus()).isEqualTo(200);
    }
}
