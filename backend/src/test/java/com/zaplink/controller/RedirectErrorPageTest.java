package com.zaplink.controller;

import com.zaplink.model.Link;
import com.zaplink.model.Role;
import com.zaplink.model.User;
import com.zaplink.repository.LinkRepository;
import com.zaplink.repository.UserRepository;
import com.zaplink.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that redirect-path errors produce styled HTML pages (not JSON envelopes),
 * and that /api/* errors are unaffected (still JSON).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RedirectErrorPageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LinkRepository linkRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> linkIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        linkIds.forEach(id -> { try { linkRepository.deleteById(id); } catch (Exception ignored) {} });
        userIds.forEach(id -> { try { userRepository.deleteById(id); } catch (Exception ignored) {} });
        linkIds.clear();
        userIds.clear();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@redir.test");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setRole(Role.USER);
        user.setIsActive(true);
        User saved = userRepository.save(user);
        userIds.add(saved.getId());
        return saved;
    }

    private Link createLink(Long userId, String shortCode, boolean active, LocalDateTime expiresAt) {
        Link link = new Link();
        link.setUserId(userId);
        link.setLongUrl("https://redir-test.example.com/" + shortCode);
        link.setShortCode(shortCode);
        link.setIsActive(active);
        link.setExpiresAt(expiresAt);
        Link saved = linkRepository.save(link);
        linkIds.add(saved.getId());
        return saved;
    }

    // ── Not Found (404) ───────────────────────────────────────────────────────

    @Test
    void redirect_unknownCode_returns404HtmlPage() throws Exception {
        mockMvc.perform(get("/totally-unknown-xyz999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Link Not Found")))
                .andExpect(content().string(containsString("ZapLink")));
    }

    @Test
    void redirect_reservedCode_returns404HtmlPage() throws Exception {
        // "admin" is a reserved short code; unlike "/api", it is not under Spring Security's
        // /api/** pattern so it reaches RedirectController and must return an HTML 404 page.
        mockMvc.perform(get("/admin"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Link Not Found")));
    }

    // ── Gone (410) ───────────────────────────────────────────────────────────

    @Test
    void redirect_disabledLink_returns410HtmlPage() throws Exception {
        User user = createUser("redir_disabled");
        createLink(user.getId(), "dis001", false, null);

        mockMvc.perform(get("/dis001"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Link Disabled")))
                .andExpect(content().string(containsString("This link has been disabled.")));
    }

    @Test
    void redirect_expiredLink_returns410HtmlPage() throws Exception {
        User user = createUser("redir_expired");
        createLink(user.getId(), "exp001", true, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/exp001"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Link Expired")))
                .andExpect(content().string(containsString("This link has expired.")));
    }

    // ── JSON path isolation ───────────────────────────────────────────────────

    @Test
    void apiLink_notFound_returnsJsonEnvelope_notHtml() throws Exception {
        User user = createUser("redir_json");
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        mockMvc.perform(get("/api/links/99999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Link not found"));
    }
}
