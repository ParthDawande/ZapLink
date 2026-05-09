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
class AdminReportsControllerTest {

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
                {"username":"%s","email":"%s@rpttest.com","password":"Password1"}
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

        String email = username + "@rpttest.com";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password1"}
                                """.formatted(email).strip()))
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

    private void softDeleteLink(String token, long linkId) throws Exception {
        mockMvc.perform(delete("/api/links/" + linkId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private void banUser(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setIsActive(false);
        userRepository.save(user);
    }

    private void setLinkExpiry(long linkId, LocalDateTime expiresAt) {
        var link = linkRepository.findById(linkId).orElseThrow();
        link.setExpiresAt(expiresAt);
        linkRepository.save(link);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void getReports_userRole_returns403() throws Exception {
        String userToken = registerAndGetToken("rpt_usr_403");

        MvcResult result = mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(403);
    }

    @Test
    void getReports_adminRole_returnsCompleteStructure() throws Exception {
        registerAndGetToken("rpt_str_admin");
        String adminToken = promoteToAdminAndLogin("rpt_str_admin");

        MvcResult result = mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        // Top-level sections
        assertThat(json.has("userStats")).isTrue();
        assertThat(json.has("linkStats")).isTrue();
        assertThat(json.has("clickStats")).isTrue();

        // userStats fields
        JsonNode u = json.get("userStats");
        assertThat(u.has("totalUsers")).isTrue();
        assertThat(u.has("activeUsers")).isTrue();
        assertThat(u.has("bannedUsers")).isTrue();
        assertThat(u.has("newUsersLast30Days")).isTrue();
        assertThat(u.get("totalUsers").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(u.get("activeUsers").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(u.get("bannedUsers").asLong()).isGreaterThanOrEqualTo(0);

        // linkStats fields
        JsonNode l = json.get("linkStats");
        assertThat(l.has("totalLinks")).isTrue();
        assertThat(l.has("activeLinks")).isTrue();
        assertThat(l.has("expiredLinks")).isTrue();
        assertThat(l.has("disabledLinks")).isTrue();
        assertThat(l.has("deletedLinks")).isTrue();
        assertThat(l.has("orphanedLinks")).isTrue();
        assertThat(l.has("newLinksLast30Days")).isTrue();

        // clickStats fields
        JsonNode c = json.get("clickStats");
        assertThat(c.has("totalClicks")).isTrue();
        assertThat(c.has("clicksLast30Days")).isTrue();
        assertThat(c.get("totalClicks").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(c.get("clicksLast30Days").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getReports_linkStatsInvariantsHold() throws Exception {
        // Invariants from API_DOCS:
        //   activeLinks + expiredLinks + disabledLinks = totalLinks
        //   orphanedLinks <= deletedLinks (all orphaned links are also deleted)
        registerAndGetToken("rpt_inv_admin");
        String adminToken = promoteToAdminAndLogin("rpt_inv_admin");

        MvcResult result = mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode l = objectMapper.readTree(result.getResponse().getContentAsString()).get("linkStats");

        long totalLinks    = l.get("totalLinks").asLong();
        long activeLinks   = l.get("activeLinks").asLong();
        long expiredLinks  = l.get("expiredLinks").asLong();
        long disabledLinks = l.get("disabledLinks").asLong();
        long deletedLinks  = l.get("deletedLinks").asLong();
        long orphanedLinks = l.get("orphanedLinks").asLong();

        assertThat(activeLinks + expiredLinks + disabledLinks)
                .as("activeLinks + expiredLinks + disabledLinks must equal totalLinks")
                .isEqualTo(totalLinks);

        assertThat(orphanedLinks)
                .as("orphanedLinks must be <= deletedLinks (orphans are a subset of deleted)")
                .isLessThanOrEqualTo(deletedLinks);
    }

    @Test
    void getReports_countsReflectKnownFixture() throws Exception {
        // Creates a controlled fixture: 1 active user + 1 banned user, 3 links
        // (1 active, 1 expired, 1 deleted), then verifies the delta in the report.
        registerAndGetToken("rpt_fix_admin");
        String adminToken = promoteToAdminAndLogin("rpt_fix_admin");

        // Baseline
        JsonNode baseline = objectMapper.readTree(
                mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse().getContentAsString());

        long baseTotal   = baseline.get("linkStats").get("totalLinks").asLong();
        long baseActive  = baseline.get("linkStats").get("activeLinks").asLong();
        long baseExpired = baseline.get("linkStats").get("expiredLinks").asLong();
        long baseDeleted = baseline.get("linkStats").get("deletedLinks").asLong();
        long baseUsers   = baseline.get("userStats").get("totalUsers").asLong();
        long baseBanned  = baseline.get("userStats").get("bannedUsers").asLong();

        // Create fixture users
        String activeUserToken = registerAndGetToken("rpt_fix_active");
        registerAndGetToken("rpt_fix_banned");
        banUser("rpt_fix_banned");

        // Create fixture links via activeUser
        long activeLinkId  = createLink(activeUserToken, "https://example.com/fixture-active");
        long expiredLinkId = createLink(activeUserToken, "https://example.com/fixture-expired");
        long deletedLinkId = createLink(activeUserToken, "https://example.com/fixture-deleted");

        setLinkExpiry(expiredLinkId, LocalDateTime.now().minusDays(1));
        softDeleteLink(activeUserToken, deletedLinkId);

        // Measure after fixture
        JsonNode after = objectMapper.readTree(
                mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse().getContentAsString());

        long afterTotal   = after.get("linkStats").get("totalLinks").asLong();
        long afterActive  = after.get("linkStats").get("activeLinks").asLong();
        long afterExpired = after.get("linkStats").get("expiredLinks").asLong();
        long afterDeleted = after.get("linkStats").get("deletedLinks").asLong();
        long afterUsers   = after.get("userStats").get("totalUsers").asLong();
        long afterBanned  = after.get("userStats").get("bannedUsers").asLong();

        // totalLinks = non-deleted links; we added 2 non-deleted (active + expired)
        assertThat(afterTotal - baseTotal).as("totalLinks delta: +2 non-deleted links").isEqualTo(2);
        assertThat(afterActive - baseActive).as("activeLinks delta: +1").isEqualTo(1);
        assertThat(afterExpired - baseExpired).as("expiredLinks delta: +1").isEqualTo(1);
        assertThat(afterDeleted - baseDeleted).as("deletedLinks delta: +1").isEqualTo(1);

        assertThat(afterUsers - baseUsers).as("totalUsers delta: +2 users").isEqualTo(2);
        assertThat(afterBanned - baseBanned).as("bannedUsers delta: +1").isEqualTo(1);

        // The unused variable — suppress it
        assert activeLinkId > 0;
    }
}
