package com.zaplink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaplink.model.DisabledReason;
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
class AdminLinkDisableTest {

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
                {"username":"%s","email":"%s@dltest.com","password":"Password1"}
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

        String email = username + "@dltest.com";
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

    private MvcResult patchDisable(String adminToken, long linkId, Boolean disabled) throws Exception {
        String body = disabled == null ? "{}" : """
                {"disabled":%s}
                """.formatted(disabled).strip();
        return mockMvc.perform(patch("/api/admin/links/" + linkId + "/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(body))
                .andReturn();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void disableLink_noToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/admin/links/1/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disabled\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void disableLink_userRole_returns403() throws Exception {
        String userToken = registerAndGetToken("dl_usr_403");

        MvcResult result = mockMvc.perform(patch("/api/admin/links/1/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + userToken)
                        .content("{\"disabled\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("message").asText()).contains("Forbidden");
    }

    @Test
    void disableLink_nonExistentId_returns404() throws Exception {
        registerAndGetToken("dl_404_admin");
        String adminToken = promoteToAdminAndLogin("dl_404_admin");

        MvcResult result = patchDisable(adminToken, 999999999L, true);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(404);
    }

    @Test
    void disableLink_activeLink_disabledTrue_returns200WithDisabledState() throws Exception {
        registerAndGetToken("dl_dis_admin");
        String adminToken = promoteToAdminAndLogin("dl_dis_admin");
        String userToken = registerAndGetToken("dl_dis_user");
        long linkId = createLink(userToken, "https://example.com/disable-active-test");

        MvcResult result = patchDisable(adminToken, linkId, true);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("isActive").asBoolean()).isFalse();
        assertThat(json.get("disabledReason").asText()).isEqualTo("ADMIN_DISABLED");
        assertThat(json.get("status").asText()).isEqualTo("disabled");
        assertThat(json.get("id").asLong()).isEqualTo(linkId);
    }

    @Test
    void disableLink_alreadyDisabled_returns400() throws Exception {
        registerAndGetToken("dl_aadis_admin");
        String adminToken = promoteToAdminAndLogin("dl_aadis_admin");
        String userToken = registerAndGetToken("dl_aadis_user");
        long linkId = createLink(userToken, "https://example.com/already-disabled-test");

        // First disable succeeds
        patchDisable(adminToken, linkId, true);

        // Second disable must fail
        MvcResult result = patchDisable(adminToken, linkId, true);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asText()).contains("already disabled");
    }

    @Test
    void disableLink_adminDisabledLink_disabledFalse_returns200WithActiveState() throws Exception {
        registerAndGetToken("dl_enbl_admin");
        String adminToken = promoteToAdminAndLogin("dl_enbl_admin");
        String userToken = registerAndGetToken("dl_enbl_user");
        long linkId = createLink(userToken, "https://example.com/re-enable-test");

        // Disable first, then re-enable
        patchDisable(adminToken, linkId, true);
        MvcResult result = patchDisable(adminToken, linkId, false);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("isActive").asBoolean()).isTrue();
        assertThat(json.get("disabledReason").isNull()).isTrue();
        assertThat(json.get("status").asText()).isEqualTo("active");
    }

    @Test
    void disableLink_alreadyActive_disabledFalse_returns400() throws Exception {
        registerAndGetToken("dl_aaact_admin");
        String adminToken = promoteToAdminAndLogin("dl_aaact_admin");
        String userToken = registerAndGetToken("dl_aaact_user");
        long linkId = createLink(userToken, "https://example.com/already-active-test");

        MvcResult result = patchDisable(adminToken, linkId, false);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asText()).contains("already active");
    }

    @Test
    void disableLink_userBannedLink_returns400WithBanMessage() throws Exception {
        registerAndGetToken("dl_ban_admin");
        String adminToken = promoteToAdminAndLogin("dl_ban_admin");
        String userToken = registerAndGetToken("dl_ban_user");
        long linkId = createLink(userToken, "https://example.com/user-banned-test");

        // Simulate USER_BANNED state directly via repository
        var link = linkRepository.findById(linkId).orElseThrow();
        link.setIsActive(false);
        link.setDisabledReason(DisabledReason.USER_BANNED);
        linkRepository.save(link);

        MvcResult result = patchDisable(adminToken, linkId, true);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asText()).contains("ban");

        // Also assert enable attempt is rejected too
        MvcResult enableResult = patchDisable(adminToken, linkId, false);
        assertThat(enableResult.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void disableLink_missingDisabledField_returns400ValidationError() throws Exception {
        registerAndGetToken("dl_val_admin");
        String adminToken = promoteToAdminAndLogin("dl_val_admin");

        MvcResult result = mockMvc.perform(patch("/api/admin/links/1/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("message").asText()).contains("disabled");
    }

    @Test
    void disableLink_softDeletedLink_returns404() throws Exception {
        registerAndGetToken("dl_del_admin");
        String adminToken = promoteToAdminAndLogin("dl_del_admin");
        String userToken = registerAndGetToken("dl_del_user");
        long linkId = createLink(userToken, "https://example.com/deleted-link-disable-test");

        // Soft-delete the link directly via repository
        var link = linkRepository.findById(linkId).orElseThrow();
        link.setDeletedAt(LocalDateTime.now());
        linkRepository.save(link);

        MvcResult result = patchDisable(adminToken, linkId, true);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(404);
    }
}
